package com.catalogix.inventory.integration;

import com.catalogix.inventory.InventorySvcApplication;
import com.catalogix.inventory.exception.IdempotencyConflictException;
import com.catalogix.inventory.exception.InsufficientInventoryException;
import com.catalogix.inventory.svc.InventorySvc;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stock ledger against a real Postgres: the invariants a mock-based test cannot prove because
 * they live in row locks and the idempotency table — no overselling under concurrency, and
 * reserve/release being exactly-once and order-independent.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = InventorySvcApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryConcurrencyIntegrationTest {

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

    @Autowired InventorySvc svc;

    private static final AtomicLong PRODUCT_SEQ = new AtomicLong(9000);

    private long newProduct(int quantity) {
        long id = PRODUCT_SEQ.incrementAndGet();
        svc.init(id, quantity);
        return id;
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        long product = newProduct(5);
        int buyers = 20;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        int reserved = 0, refused = 0;
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                final String op = "oversell-" + product + "-" + i;
                calls.add(() -> {
                    go.await(10, TimeUnit.SECONDS);
                    try {
                        svc.adjust(product, -1, op, null);
                        return true;
                    } catch (InsufficientInventoryException e) {
                        return false;
                    }
                });
            }
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> c : calls) futures.add(pool.submit(c));
            go.countDown();
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) reserved++; else refused++;
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(reserved).as("exactly the 5 units in stock were sold").isEqualTo(5);
        assertThat(refused).isEqualTo(15);
        assertThat(svc.get(product).getQuantity()).isZero();
    }

    @Test
    void replayingAReservationDeductsOnlyOnce() {
        long product = newProduct(10);

        svc.adjust(product, -3, "replay-1", null);
        svc.adjust(product, -3, "replay-1", null); // client retried after a timeout

        assertThat(svc.get(product).getQuantity()).isEqualTo(7);
    }

    @Test
    void reusingAnOperationIdForADifferentAdjustmentIsRejected() {
        long product = newProduct(10);
        svc.adjust(product, -3, "conflict-1", null);

        assertThatThrownBy(() -> svc.adjust(product, -4, "conflict-1", null))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(svc.get(product).getQuantity()).isEqualTo(7);
    }

    @Test
    void aReleaseGivesBackExactlyTheNamedReservationAndIsIdempotent() {
        long product = newProduct(10);
        svc.adjust(product, -2, "res-A", null);
        assertThat(svc.get(product).getQuantity()).isEqualTo(8);

        svc.adjust(product, 2, "rel-A", "res-A");
        svc.adjust(product, 2, "rel-A", "res-A"); // replayed release

        assertThat(svc.get(product).getQuantity()).as("restocked once, not twice").isEqualTo(10);
    }

    @Test
    void aReleaseForAReservationThatNeverArrivedAddsNothingAndBlocksALateArrival() {
        long product = newProduct(10);

        // Compensation ran after the reserve call timed out; inventory-svc never saw it.
        svc.adjust(product, 2, "rel-late", "res-late");
        assertThat(svc.get(product).getQuantity()).as("nothing to undo -> nothing added").isEqualTo(10);

        // ...and the slow original request finally lands. It must not deduct stock for an order that was abandoned.
        assertThatThrownBy(() -> svc.adjust(product, -2, "res-late", null))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(svc.get(product).getQuantity()).isEqualTo(10);
    }
}
