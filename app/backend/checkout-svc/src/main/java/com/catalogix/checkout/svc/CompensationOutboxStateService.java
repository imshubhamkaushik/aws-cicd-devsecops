package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.CompensationType;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Short database transactions for leasing and updating outbox rows. Remote HTTP work is never done here. */
@Service
public class CompensationOutboxStateService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(2);
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final CompensationOutboxRepository repository;

    public CompensationOutboxStateService(CompensationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<CompensationTask> claimPendingBatch() {
        Instant now = Instant.now();
        List<CompensationOutbox> rows = repository.claimPendingBatch(now, now.minus(PROCESSING_LEASE));
        List<CompensationTask> tasks = new java.util.ArrayList<>();
        for (CompensationOutbox row : rows) {
            if (row.getStatus() == OutboxStatus.PROCESSING && row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(OutboxStatus.DEAD_LETTER);
                row.setLastError("Processing lease expired after retry budget was exhausted");
                row.setUpdatedAt(now);
                continue;
            }
            row.setStatus(OutboxStatus.PROCESSING);
            row.setAttempts(row.getAttempts() + 1);
            row.setUpdatedAt(now);
            tasks.add(new CompensationTask(row.getId(), row.getType(), row.getProductId(), row.getDelta(),
                    row.getOperationId(), row.getUndoOf(), row.getAttempts()));
        }
        repository.saveAll(rows);
        return List.copyOf(tasks);
    }

    @Transactional
    public void markCompleted(Long id) {
        CompensationOutbox row = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Missing compensation outbox row " + id));
        row.setStatus(OutboxStatus.COMPLETED);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
    }

    @Transactional
    public void recordFailure(Long id, String errorMessage) {
        CompensationOutbox row = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Missing compensation outbox row " + id));
        // attempts is incremented atomically when the row is claimed, so a worker crash
        // after claim still consumes an attempt after the lease is reclaimed.
        int attempts = row.getAttempts();
        Instant now = Instant.now();
        row.setLastError(truncate(errorMessage, 500));
        row.setUpdatedAt(now);
        if (attempts >= MAX_ATTEMPTS) {
            row.setStatus(OutboxStatus.DEAD_LETTER);
        } else {
            long multiplier = 1L << Math.min(attempts - 1, 10);
            Duration delay = BASE_RETRY_DELAY.multipliedBy(multiplier);
            if (delay.compareTo(MAX_RETRY_DELAY) > 0) delay = MAX_RETRY_DELAY;
            row.setStatus(OutboxStatus.PENDING);
            row.setNextAttemptAt(now.plus(delay));
        }
        repository.save(row);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "Unknown downstream error";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record CompensationTask(Long id, CompensationType type, Long productId, Integer delta,
                                   String operationId, String undoOf, int attempts) { }
}
