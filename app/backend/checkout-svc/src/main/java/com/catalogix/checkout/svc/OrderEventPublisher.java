package com.catalogix.checkout.svc;

import com.catalogix.checkout.event.OrderCancelledEvent;
import com.catalogix.checkout.event.OrderConfirmedEvent;
import com.catalogix.checkout.model.OrderEventOutbox;
import com.catalogix.checkout.repository.OrderEventOutboxRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Persists order notifications in the same transaction as the order transition; no broker I/O here. */
@Component
public class OrderEventPublisher {

    private final OrderEventOutboxRepository repository;
    private final JsonMapper mapper;

    public OrderEventPublisher(OrderEventOutboxRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @EventListener
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        enqueue("order.confirmed", event);
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        enqueue("order.cancelled", event);
    }

    private void enqueue(String routingKey, Object event) {
        try {
            repository.save(new OrderEventOutbox(routingKey, mapper.writeValueAsString(event)));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Let the enclosing order transaction fail: a confirmed order without its durable
            // notification record would be silently unrecoverable after a process crash.
            throw new IllegalStateException("Could not serialize " + routingKey + " event", e);
        }
    }
}
