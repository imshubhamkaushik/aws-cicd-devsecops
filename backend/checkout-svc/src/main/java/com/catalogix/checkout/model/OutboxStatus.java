package com.catalogix.checkout.model;

public enum OutboxStatus {
    PENDING,
    COMPLETED,
    // Exceeded max retry attempts — needs manual attention/alerting; the
    // scheduled processor stops touching it once it reaches this state.
    DEAD_LETTER
}
