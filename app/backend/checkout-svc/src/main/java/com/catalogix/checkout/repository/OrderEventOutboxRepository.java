package com.catalogix.checkout.repository;

import com.catalogix.checkout.model.OrderEventOutbox;
import com.catalogix.checkout.model.OrderEventOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OrderEventOutboxRepository extends JpaRepository<OrderEventOutbox, Long> {

    long countByStatus(OrderEventOutboxStatus status);

    // LIMIT 10 on purpose: 10 rows x 10s worst-case broker confirm = 100s, below the 2-minute lease.
    // The batch must finish inside the lease, or another replica could reclaim rows still in progress.
    @Query(value = """
        SELECT * FROM order_event_outbox
        WHERE (status = 'PENDING' AND next_attempt_at <= :eligibleAt)
           OR (status = 'PROCESSING' AND updated_at < :staleBefore)
        ORDER BY created_at ASC
        LIMIT 10
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OrderEventOutbox> claimDueBatch(Instant eligibleAt, Instant staleBefore);
}
