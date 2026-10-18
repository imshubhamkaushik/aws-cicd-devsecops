package com.catalogix.checkout.model;

public enum OrderEventOutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    DEAD_LETTER
}
