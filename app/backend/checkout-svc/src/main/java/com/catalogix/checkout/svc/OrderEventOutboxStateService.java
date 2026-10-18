package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.OrderEventOutbox;
import com.catalogix.checkout.model.OrderEventOutboxStatus;
import com.catalogix.checkout.repository.OrderEventOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Transactional lease/state transitions for order events; broker calls happen outside these methods. */
@Service
public class OrderEventOutboxStateService {

    private static final int MAX_ATTEMPTS = 8;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration BASE_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_DELAY = Duration.ofMinutes(10);
    private final OrderEventOutboxRepository repository;

    public OrderEventOutboxStateService(OrderEventOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<OrderEventTask> claimDueBatch() {
        Instant now = Instant.now();
        List<OrderEventOutbox> rows = repository.claimDueBatch(now, now.minus(LEASE));
        List<OrderEventTask> tasks = new java.util.ArrayList<>();
        for (OrderEventOutbox row : rows) {
            if (row.getStatus() == OrderEventOutboxStatus.PROCESSING && row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(OrderEventOutboxStatus.DEAD_LETTER);
                row.setLastError("Processing lease expired after retry budget was exhausted");
                row.setUpdatedAt(now);
                continue;
            }
            row.setStatus(OrderEventOutboxStatus.PROCESSING);
            row.setAttempts(row.getAttempts() + 1);
            row.setUpdatedAt(now);
            tasks.add(new OrderEventTask(row.getId(), row.getRoutingKey(), row.getPayload(), row.getAttempts()));
        }
        repository.saveAll(rows);
        return List.copyOf(tasks);
    }

    @Transactional
    public void markPublished(Long id) {
        OrderEventOutbox row = requireRow(id);
        row.setStatus(OrderEventOutboxStatus.PUBLISHED);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
    }

    @Transactional
    public void recordFailure(Long id, String error) {
        OrderEventOutbox row = requireRow(id);
        // Count the attempt at claim time, including attempts lost to a crashed worker.
        int attempts = row.getAttempts();
        Instant now = Instant.now();
        row.setLastError(truncate(error, 500));
        row.setUpdatedAt(now);
        if (attempts >= MAX_ATTEMPTS) {
            row.setStatus(OrderEventOutboxStatus.DEAD_LETTER);
        } else {
            long multiplier = 1L << Math.min(attempts - 1, 10);
            Duration delay = BASE_DELAY.multipliedBy(multiplier);
            row.setNextAttemptAt(now.plus(delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay));
            row.setStatus(OrderEventOutboxStatus.PENDING);
        }
        repository.save(row);
    }

    private OrderEventOutbox requireRow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Missing order event outbox row " + id));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "Unknown broker error";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record OrderEventTask(Long id, String routingKey, String payload, int attempts) { }
}
