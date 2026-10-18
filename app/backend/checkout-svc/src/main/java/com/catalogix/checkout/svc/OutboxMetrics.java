package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.OrderEventOutboxStatus;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;
import com.catalogix.checkout.repository.OrderEventOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Exposes live backlog and dead-letter gauges for both checkout outboxes. */
@Component
public class OutboxMetrics {

    private final CompensationOutboxRepository compensationRepository;
    private final OrderEventOutboxRepository orderEventRepository;
    private final MeterRegistry meterRegistry;

    public OutboxMetrics(
            CompensationOutboxRepository compensationRepository,
            OrderEventOutboxRepository orderEventRepository,
            MeterRegistry meterRegistry) {
        this.compensationRepository = compensationRepository;
        this.orderEventRepository = orderEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("checkout_outbox_dead_letter_count", compensationRepository,
                        repo -> repo.countByStatus(OutboxStatus.DEAD_LETTER))
                .description("Compensation-outbox entries that exhausted retries and need manual attention")
                .register(meterRegistry);
        Gauge.builder("checkout_outbox_pending_count", compensationRepository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Compensation-outbox entries awaiting retry")
                .register(meterRegistry);
        Gauge.builder("checkout_order_event_outbox_dead_letter_count", orderEventRepository,
                        repo -> repo.countByStatus(OrderEventOutboxStatus.DEAD_LETTER))
                .description("Order-event outbox entries that exhausted retries and need manual attention")
                .register(meterRegistry);
        Gauge.builder("checkout_order_event_outbox_pending_count", orderEventRepository,
                        repo -> repo.countByStatus(OrderEventOutboxStatus.PENDING))
                .description("Order-event outbox entries awaiting publish retry")
                .register(meterRegistry);
    }
}
