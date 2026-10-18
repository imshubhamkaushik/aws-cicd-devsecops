package com.catalogix.checkout.repository;

import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface CompensationOutboxRepository extends JpaRepository<CompensationOutbox, Long> {

    List<CompensationOutbox> findByStatusInOrderByCreatedAtDesc(List<OutboxStatus> statuses);

    /** Backs the outbox_dead_letter_count gauge (see OutboxMetrics). */
    long countByStatus(OutboxStatus status);

    /**
     * FOR UPDATE SKIP LOCKED claims each pending row for exactly one replica, so replicas running
     * the scheduled job concurrently split the batch instead of double-releasing stock.
     */
    // LIMIT 10 on purpose: 10 rows x ~11s worst-case HTTP call = 110s, below the 2-minute lease.
    // The batch must finish inside the lease, or another replica could reclaim rows still in progress.
    @Query(value = """
        SELECT * FROM compensation_outbox
        WHERE (status = 'PENDING' AND next_attempt_at <= :eligibleAt)
           OR (status = 'PROCESSING' AND updated_at < :staleBefore)
        ORDER BY created_at ASC
        LIMIT 10
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<CompensationOutbox> claimPendingBatch(Instant eligibleAt, Instant staleBefore);
}
