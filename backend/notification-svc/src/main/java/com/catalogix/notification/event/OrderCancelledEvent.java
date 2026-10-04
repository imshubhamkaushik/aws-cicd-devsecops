package com.catalogix.notification.event;

import java.time.Instant;
import java.util.List;

public record OrderCancelledEvent(Long orderId, Long userId, String userEmail, List<OrderItemEventData> items, Instant occurredAt) {
    public OrderCancelledEvent(Long orderId, Long userId, String userEmail, Instant occurredAt) {
        this(orderId, userId, userEmail, List.of(), occurredAt);
    }
}
