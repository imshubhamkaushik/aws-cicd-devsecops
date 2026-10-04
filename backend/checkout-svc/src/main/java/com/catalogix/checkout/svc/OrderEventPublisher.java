package com.catalogix.checkout.svc;

import com.catalogix.checkout.config.RabbitMQConfig;
import com.catalogix.checkout.event.OrderCancelledEvent;
import com.catalogix.checkout.event.OrderConfirmedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays in-JVM order events to RabbitMQ for notification-svc to consume.
 *
 * Publishing happens AFTER_COMMIT, so a rolled-back order never produces an event. Delivery is
 * best-effort: there is no retry or outbox if RabbitMQ is unreachable, which is acceptable for
 * confirmation emails. This is a separate bean because @TransactionalEventListener and @Async are
 * proxy-based and only apply to events published through ApplicationEventPublisher.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        publish("order.confirmed", event, event.orderId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        publish("order.cancelled", event, event.orderId());
    }


    private void publish(String routingKey, Object event, Long orderId) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, routingKey, event);
        } catch (RuntimeException e) {
            log.warn("Failed to publish {} for order {}: {}", routingKey, orderId, e.getMessage());
        }
    }
}
