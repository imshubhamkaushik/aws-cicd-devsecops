package com.catalogix.notification.event;

import java.math.BigDecimal;

public record OrderItemEventData(String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
}
