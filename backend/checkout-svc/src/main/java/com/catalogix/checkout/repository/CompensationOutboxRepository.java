package com.catalogix.checkout.repository;

import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompensationOutboxRepository extends JpaRepository<CompensationOutbox, Long> {

    List<CompensationOutbox> findByStatusInOrderByCreatedAtDesc(List<OutboxStatus> statuses);

    /** Backs the outbox_dead_letter_count gauge (see OutboxMetrics). */
    long countByStatus(OutboxStatus status);

    /**
     * FOR UPDATE SKIP LOCKED claims each pending row for exactly one replica, so replicas running
     * the scheduled job concurrently split the batch instead of double-releasing stock.
     */
    @Query(value = """
        SELECT * FROM compensation_outbox
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT 50
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<CompensationOutbox> claimPendingBatch();
}
