package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Exposes gauges for the outbox's DEAD_LETTER and PENDING counts so stuck compensations are
 * visible to Prometheus. A Gauge fits because the counts can fall as well as rise, and the
 * supplier is read live on every scrape.
 */
@Component
public class OutboxMetrics {

    private final CompensationOutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;

    public OutboxMetrics(CompensationOutboxRepository outboxRepository, MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("checkout_outbox_dead_letter_count",
                        outboxRepository,
                        repo -> repo.countByStatus(OutboxStatus.DEAD_LETTER))
                .description("Number of compensation-outbox entries stuck in DEAD_LETTER — exceeded max retry attempts and need manual attention")
                .register(meterRegistry);

        // Pending backlog is an earlier warning sign than dead letters.
        Gauge.builder("checkout_outbox_pending_count",
                        outboxRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Number of compensation-outbox entries still PENDING processing")
                .register(meterRegistry);
    }
}
