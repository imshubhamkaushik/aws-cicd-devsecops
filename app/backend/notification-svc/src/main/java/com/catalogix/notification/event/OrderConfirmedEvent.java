package com.catalogix.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Mirrors checkout-svc's event of the same name — the JSON message on the wire
// (via Jackson2JsonMessageConverter) is the actual contract between services,
// not a shared Java class; each service keeps its own copy, consistent with
// how JwtService/PagedResponse etc. are duplicated rather than shared elsewhere.
public record OrderConfirmedEvent(
        Long orderId, Long userId, String userEmail, List<OrderItemEventData> items, BigDecimal totalAmount, Instant occurredAt
) {
}
