package com.catalogix.payment.integration;

import com.catalogix.payment.PaymentSvcApplication;
import com.catalogix.payment.dto.ProcessPaymentRequest;
import com.catalogix.payment.dto.ProcessRefundRequest;
import com.catalogix.payment.dto.RefundResponse;
import com.catalogix.payment.exception.DeclinedException;
import com.catalogix.payment.model.PaymentMethod;
import com.catalogix.payment.svc.PaymentSvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payment-svc's idempotency and refund guarantees against a real Postgres — unique indexes, the
 * noRollbackFor decline path and row locks, none of which a mock repository exercises.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = PaymentSvcApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentIdempotencyIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("JWT_SECRET", () -> "integration-test-only-secret-not-used-anywhere-else-0123456789");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired PaymentSvc svc;
    @Autowired JdbcTemplate jdbc;

    private static final AtomicLong SEQ = new AtomicLong(20_000);

    private static ProcessPaymentRequest card(long orderId, String last4, String amount) {
        ProcessPaymentRequest req = new ProcessPaymentRequest();
        req.setOrderId(orderId);
        req.setRequestedByUserId(1L); // overwritten per call below where a specific user matters
        req.setAmount(new BigDecimal(amount));
        req.setMethod(PaymentMethod.CARD);
        req.setCardLast4(last4);
        return req;
    }

    private int paymentRows(long userId, String key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE requested_by_user_id = ? AND idempotency_key = ?",
                Integer.class, userId, key);
    }

    @Test
    void theSameIdempotencyKeyReplaysTheFirstResultAndChargesOnce() {
        long user = SEQ.incrementAndGet(), order = SEQ.incrementAndGet();

        PaymentSvc.ProcessResult first = svc.process(card(order, "4242", "100.00"), user, "pay-1");
        PaymentSvc.ProcessResult second = svc.process(card(order, "4242", "100.00"), user, "pay-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response().getId()).isEqualTo(first.response().getId());
        assertThat(paymentRows(user, "pay-1")).isEqualTo(1);
    }

    @Test
    void aDeclinedPaymentIsRecordedDespiteTheExceptionAndReplaysAsDeclined() {
        long user = SEQ.incrementAndGet();
        long order = SEQ.incrementAndGet();
        ProcessPaymentRequest request = card(order, "0000", "100.00");

        assertThatThrownBy(() -> svc.process(request, user, "decl-1"))
                .isInstanceOf(DeclinedException.class);

        // noRollbackFor: the FAILED row must survive the exception,
        // or a retry would re-attempt the card.
        assertThat(paymentRows(user, "decl-1")).isEqualTo(1);

        assertThatThrownBy(() -> svc.process(request, user, "decl-1"))
                .isInstanceOf(DeclinedException.class);

        assertThat(paymentRows(user, "decl-1")).isEqualTo(1);
    }

    @Test
    void twoConcurrentRequestsWithTheSameKeyLeaveExactlyOnePayment() throws Exception {
        long user = SEQ.incrementAndGet(), order = SEQ.incrementAndGet();
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int succeeded = 0;
        try {
            List<Callable<Object>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return svc.process(card(order, "4242", "100.00"), user, "race-1");
                    } catch (Exception e) {
                        // The loser of the INSERT race may surface a constraint violation; what matters is
                        // that the unique index kept the ledger to one row (checkout-svc treats a failed
                        // call as "unknown outcome" and retries with the same key, which then replays).
                        return e;
                    }
                });
            }
            for (Future<Object> f : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) {
                if (f.get() instanceof PaymentSvc.ProcessResult) succeeded++;
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded).as("at least one request got a real answer").isGreaterThanOrEqualTo(1);
        assertThat(paymentRows(user, "race-1")).as("never two charges for one key").isEqualTo(1);
    }

    /**
     * CHARACTERISATION, not an endorsement: payment-svc only de-duplicates by (user, Idempotency-Key). Two
     * requests for the same ORDER with different keys are both charged. That is why checkout-svc
     * serialises payment per order (the PAYMENT_PROCESSING claim) — remove that and this is a double
     * charge. If payment-svc later gains a one-success-per-order guard, this test should be inverted.
     */
    @Test
    void differentKeysForTheSameOrderAreBothCharged_soCheckoutMustSerialisePerOrder() {
        long user = SEQ.incrementAndGet(), order = SEQ.incrementAndGet();

        svc.process(card(order, "4242", "100.00"), user, "order-key-a");
        svc.process(card(order, "4242", "100.00"), user, "order-key-b");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'SUCCEEDED'", Integer.class, order))
                .isEqualTo(2);
    }

    @Test
    void refundsCanNeverExceedTheOriginalPayment() {
        long user = SEQ.incrementAndGet();
        long order = SEQ.incrementAndGet();

        svc.process(card(order, "4242", "100.00"), user, "refund-base");

        svc.refund(refund(order, "60.00"), "refund-1");

        ProcessRefundRequest secondRefund = refund(order, "60.00");

        assertThatThrownBy(() -> svc.refund(secondRefund, "refund-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would exceed");

        assertThat(jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE order_id = ?",
                BigDecimal.class,
                order))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void replayingARefundWithTheSameKeyReturnsTheSameRefund() {
        long user = SEQ.incrementAndGet(), order = SEQ.incrementAndGet();
        svc.process(card(order, "4242", "100.00"), user, "refund-base-2");

        RefundResponse first = svc.refund(refund(order, "40.00"), "same-refund");
        RefundResponse second = svc.refund(refund(order, "40.00"), "same-refund");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE order_id = ?", Integer.class, order))
                .isEqualTo(1);
    }

    private static ProcessRefundRequest refund(long orderId, String amount) {
        ProcessRefundRequest req = new ProcessRefundRequest();
        req.setOrderId(orderId);
        req.setAmount(new BigDecimal(amount));
        return req;
    }
}
