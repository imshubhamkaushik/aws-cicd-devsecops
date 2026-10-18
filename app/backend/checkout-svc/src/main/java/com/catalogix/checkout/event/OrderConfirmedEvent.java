package com.catalogix.checkout.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Published when an order's payment succeeds and relayed to RabbitMQ after the transaction
 * commits (see OrderEventPublisher). notification-svc turns it into an email. userId lets
 * notification-svc check the recipient's notification preferences before sending.
 */
public record OrderConfirmedEvent(
        Long orderId,
        Long userId,
        String userEmail,
        List<OrderItemEventData> items,
        BigDecimal totalAmount,
        Instant occurredAt
) {
    public OrderConfirmedEvent(Long orderId, Long userId, String userEmail, List<OrderItemEventData> items, BigDecimal totalAmount) {
        this(orderId, userId, userEmail, items, totalAmount, Instant.now());
    }
}
