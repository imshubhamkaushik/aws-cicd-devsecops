package com.catalogix.checkout.integration;

import com.catalogix.checkout.CheckoutSvcApplication;
import com.catalogix.checkout.client.AddressClient;
import com.catalogix.checkout.client.CartClient;
import com.catalogix.checkout.client.CatalogClient;
import com.catalogix.checkout.client.InventoryClient;
import com.catalogix.checkout.client.PaymentClient;
import com.catalogix.checkout.client.RefundClient;
import com.catalogix.checkout.dto.CreateOrderRequest;
import com.catalogix.checkout.dto.OrderItemRequest;
import com.catalogix.checkout.dto.PayOrderRequest;
import com.catalogix.checkout.exception.InvalidOrderStateException;
import com.catalogix.checkout.model.PaymentMethod;
import com.catalogix.checkout.svc.CheckoutSvc;
import com.catalogix.checkout.svc.PendingOrderExpiryJob;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The money path against a REAL Postgres with the REAL Flyway migrations (ddl-auto=validate, so
 * an entity/schema mismatch — e.g. a forgotten migration — fails the whole class), real
 * transactions and real row locks. Only the HTTP clients to other services are mocked.
 *
 * Every unit test of CheckoutSvc uses mocks and cannot see: whether the claim actually
 * serialises two requests, whether a connection is held while payment-svc is called, whether the
 * unique index really rejects a duplicate idempotency key, whether the AFTER_COMMIT event still
 * fires from a programmatic transaction. These tests can.
 *
 * Needs Docker (Testcontainers); runs in failsafe via {@code mvn verify}, tagged "integration".
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = CheckoutSvcApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CheckoutPaymentIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("JWT_SECRET", () -> "integration-test-only-secret-not-used-anywhere-else-0123456789");

        // Real schema, exactly as production builds it. (application-test.properties turns Flyway
        // off and uses create-drop for the slice tests; re-enabled here so the migrations —
        // including V13__payment_in_progress — are what is under test.)
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.sql.init.mode", () -> "never");

        // Background jobs must not race the assertions; tests call sweep() themselves.
        registry.add("PENDING_ORDER_SWEEP_MS", () -> "3600000");
        registry.add("OUTBOX_POLL_INTERVAL_MS", () -> "3600000");
    }

    // Every other service is mocked at its HTTP client. RabbitTemplate is mocked so the
    // AFTER_COMMIT event relay can be asserted without a broker.
    @MockitoBean PaymentClient paymentClient;
    @MockitoBean InventoryClient inventoryClient;
    @MockitoBean CatalogClient catalogClient;
    @MockitoBean CartClient cartClient;
    @MockitoBean AddressClient addressClient;
    @MockitoBean RefundClient refundClient;
    @MockitoBean RabbitTemplate rabbitTemplate;

    @Autowired CheckoutSvc svc;
    @Autowired PendingOrderExpiryJob job;
    @Autowired JdbcTemplate jdbc;

    private static final String TOKEN = "Bearer integration";
    private static final String EMAIL = "buyer@example.com";
    private static final AtomicInteger USER_SEQ = new AtomicInteger(1000);

    private static long newUserId() {
        return USER_SEQ.incrementAndGet();
    }

    private void stubCatalog() {
        when(catalogClient.fetch(anyLong(), any()))
                .thenReturn(new CatalogClient.ProductDto(1L, "Phone", new BigDecimal("100.00"), 42L));
    }

    private CreateOrderRequest oneItem() {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(1L);
        item.setQuantity(2);
        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(item));
        return req;
    }

    private Long placeOrder(long userId) {
        stubCatalog();
        return svc.createOrder(userId, oneItem(), TOKEN, null).order().getId();
    }

    private PayOrderRequest card() {
        PayOrderRequest req = new PayOrderRequest();
        req.setMethod(PaymentMethod.CARD);
        req.setCardLast4("4242");
        return req;
    }

    private String statusOf(Long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private static PaymentClient.PaymentOutcome approved() {
        return new PaymentClient.PaymentOutcome(true, "REF-1", "SUCCEEDED");
    }

    // ---------------------------------------------------------------------------------------

    @Test
    void anOrderRoundTripsThroughTheRealSchemaAndStartsUnclaimed() {
        long userId = newUserId();

        Long orderId = placeOrder(userId);

        assertThat(statusOf(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT payment_started_at FROM orders WHERE id = ?",
                java.sql.Timestamp.class, orderId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void twoConcurrentPaymentsForOneOrderChargeExactlyOnce_evenWithDifferentIdempotencyKeys() throws Exception {
        long userId = newUserId();
        Long orderId = placeOrder(userId);

        AtomicInteger charges = new AtomicInteger();
        CountDownLatch paymentStarted = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);

        when(paymentClient.process(
                eq(orderId),
                any(),
                any(),
                any(PayOrderRequest.class),
                anyString()))
                .thenAnswer(inv -> {
                    charges.incrementAndGet();
                    paymentStarted.countDown();

                    if (!releasePayment.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for test to release payment call");
                    }

                    return approved();
                });

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Callable<Object>> calls = new ArrayList<>();

            for (int i = 0; i < 2; i++) {
                final String key = "key-" + i;

                calls.add(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return svc.payOrder(
                                orderId,
                                userId,
                                "USER",
                                card(),
                                EMAIL,
                                key);
                    } catch (InvalidOrderStateException e) {
                        return e;
                    }
                });
            }

            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> call : calls) {
                futures.add(pool.submit(call));
            }

            assertThat(paymentStarted.await(10, TimeUnit.SECONDS))
                    .as("one payment request should reach payment-svc")
                    .isTrue();

            // The first payment is deliberately held here so the second request
            // has a chance to encounter PAYMENT_PROCESSING.
            releasePayment.countDown();

            long confirmed = 0;
            long rejected = 0;

            for (Future<Object> future : futures) {
                Object result = future.get(30, TimeUnit.SECONDS);

                if (result instanceof InvalidOrderStateException) {
                    rejected++;
                } else {
                    confirmed++;
                }
            }

            assertThat(confirmed)
                    .as("exactly one request pays")
                    .isEqualTo(1);

            assertThat(rejected)
                    .as("the other is turned away")
                    .isEqualTo(1);

        } finally {
            releasePayment.countDown();
            pool.shutdownNow();
        }

        assertThat(charges.get())
                .as("payment-svc was called once — no double charge")
                .isEqualTo(1);

        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
    }

    /**
     * Guards against holding a DB transaction across the payment call. The pool is 5 connections
     * with a 3s acquisition timeout. 12 customers pay at once and payment-svc takes 2s: if each
     * request held its connection for the whole call, the third wave of requests would wait ~4s
     * and fail with a connection timeout. With short transactions they all finish in about 2s.
     */
    @Test
    void slowPaymentCallsDoNotExhaustTheConnectionPool() throws Exception {
        int customers = 12;

        List<long[]> orders = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            long userId = newUserId();
            orders.add(new long[] { userId, placeOrder(userId) });
        }

        CountDownLatch paymentCallsEntered = new CountDownLatch(customers);
        CountDownLatch releasePayments = new CountDownLatch(1);

        when(paymentClient.process(
                anyLong(),
                any(),
                any(),
                any(PayOrderRequest.class),
                anyString()))
                .thenAnswer(inv -> {
                    paymentCallsEntered.countDown();

                    if (!releasePayments.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting for test to release payments");
                    }

                    return approved();
                });

        ExecutorService pool = Executors.newFixedThreadPool(customers);

        try {
            List<Future<String>> futures = new ArrayList<>();

            for (long[] order : orders) {
                futures.add(pool.submit(() -> {
                    svc.payOrder(
                            order[1],
                            order[0],
                            "USER",
                            card(),
                            EMAIL,
                            "pool-" + order[1]);

                    return statusOf(order[1]);
                }));
            }

            /*
             * All 12 remote payment calls must be able to reach this point
             * before any of them is released.
             *
             * If CheckoutSvc held a DB connection while calling payment-svc,
             * the 5-connection pool would allow only the first wave to proceed;
             * the remaining requests would block waiting for connections.
             */
            assertThat(paymentCallsEntered.await(10, TimeUnit.SECONDS))
                    .as("all payment calls should execute without exhausting the DB pool")
                    .isTrue();

            releasePayments.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS))
                        .isEqualTo("CONFIRMED");
            }

        } finally {
            releasePayments.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aDeclinedPaymentCancelsTheOrderAndReleasesItsStock() {
        long userId = newUserId();
        Long orderId = placeOrder(userId);
        when(paymentClient.process(eq(orderId), any(), any(), any(PayOrderRequest.class)))
                .thenReturn(new PaymentClient.PaymentOutcome(false, null, "FAILED"));

        CheckoutSvc.OrderPaymentResult result = svc.payOrder(orderId, userId, "USER", card(), EMAIL);

        assertThat(result.paymentSucceeded()).isFalse();
        assertThat(statusOf(orderId)).isEqualTo("CANCELLED");
        verify(inventoryClient).adjust(eq(1L), eq(2), any(), any()); // +2 back
    }

    @Test
    void anUnknownPaymentOutcomeReleasesTheClaimSoTheCustomerCanRetry() {
        long userId = newUserId();
        Long orderId = placeOrder(userId);

        when(paymentClient.process(
                eq(orderId),
                any(),
                any(),
                any(PayOrderRequest.class),
                eq("retry-key")))
                .thenThrow(new IllegalStateException("payment-svc timed out"))
                .thenReturn(approved());

        PayOrderRequest request = card();

        assertThatThrownBy(() -> svc.payOrder(
                orderId,
                userId,
                "USER",
                request,
                EMAIL,
                "retry-key"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(statusOf(orderId))
                .as("claim released, not stuck")
                .isEqualTo("PENDING_PAYMENT");

        verify(inventoryClient, never())
                .adjust(eq(1L), eq(2), any(), any());

        svc.payOrder(
                orderId,
                userId,
                "USER",
                request,
                EMAIL,
                "retry-key");

        assertThat(statusOf(orderId)).isEqualTo("CONFIRMED");
    }

    @Test
    void aConfirmedPaymentStillPublishesItsEventAfterTheProgrammaticTransactionCommits() {
        long userId = newUserId();
        Long orderId = placeOrder(userId);
        when(paymentClient.process(eq(orderId), any(), any(), any(PayOrderRequest.class), anyString()))
                .thenReturn(approved());

        svc.payOrder(orderId, userId, "USER", card(), EMAIL, "evt-key");

        // The relay is @TransactionalEventListener(AFTER_COMMIT) + @Async.
        verify(rabbitTemplate, timeout(5000)).convertAndSend(
                eq("catalogix.events"), eq("order.confirmed"), any(Object.class));
    }

    @Test
    void theSweepReturnsAnAbandonedClaimToPendingButLeavesAFreshOneAlone() {
        Long stranded = placeOrder(newUserId());
        Long inFlight = placeOrder(newUserId());
        jdbc.update("UPDATE orders SET status = 'PAYMENT_PROCESSING',"
                + " payment_started_at = now() - interval '20 minutes' WHERE id = ?", stranded);
        jdbc.update("UPDATE orders SET status = 'PAYMENT_PROCESSING',"
                + " payment_started_at = now() WHERE id = ?", inFlight);

        job.sweep();

        assertThat(statusOf(stranded)).isEqualTo("PENDING_PAYMENT");
        assertThat(statusOf(inFlight)).isEqualTo("PAYMENT_PROCESSING");
    }

    @Test
    void theUnpaidOrderWindowRestartsWithEachPaymentAttempt() {
        Long attempted = placeOrder(newUserId());
        Long abandoned = placeOrder(newUserId());
        jdbc.update("UPDATE orders SET created_at = now() - interval '40 minutes',"
                + " payment_started_at = now() - interval '1 minute' WHERE id = ?", attempted);
        jdbc.update("UPDATE orders SET created_at = now() - interval '40 minutes' WHERE id = ?", abandoned);

        job.sweep();

        assertThat(statusOf(attempted)).as("customer tried to pay a minute ago").isEqualTo("PENDING_PAYMENT");
        assertThat(statusOf(abandoned)).isEqualTo("CANCELLED");
    }

    /**
     * Two requests with the same Idempotency-Key (a double-click) share reservation ids, so
     * inventory-svc counts ONE reservation. The request that loses the INSERT must NOT release it.
     */
    @Test
    void aConcurrentDuplicateSubmissionDoesNotReleaseTheWinnersStock() throws Exception {
        long userId = newUserId();
        String key = "double-click-" + userId;
        CyclicBarrier bothPastTheExistingOrderCheck = new CyclicBarrier(2);
        when(catalogClient.fetch(anyLong(), any())).thenAnswer(inv -> {
            bothPastTheExistingOrderCheck.await(10, TimeUnit.SECONDS);
            return new CatalogClient.ProductDto(1L, "Phone", new BigDecimal("100.00"), 42L);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> {
                    try {
                        return svc.createOrder(userId, oneItem(), TOKEN, key);
                    } catch (DataIntegrityViolationException e) {
                        return e; // the loser; OrderController turns this into "return the existing order"
                    }
                });
            }
            List<Future<Object>> results = pool.invokeAll(calls, 30, TimeUnit.SECONDS);
            int created = 0, lostRace = 0;
            for (Future<Object> f : results) {
                Object r = f.get();
                if (r instanceof DataIntegrityViolationException) lostRace++;
                else created++;
            }
            assertThat(created).isEqualTo(1);
            assertThat(lostRace).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ? AND idempotency_key = ?", Integer.class, userId, key))
                .isEqualTo(1);
        verify(inventoryClient, times(2)).adjust(eq(1L), eq(-2), any(), any()); // both reserved (same op id)
        verify(inventoryClient, never()).adjust(eq(1L), eq(2), any(), any());   // nobody released the shared reservation
    }
}
