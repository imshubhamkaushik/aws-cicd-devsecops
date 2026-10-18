package com.catalogix.checkout.model;

public enum OutboxStatus {
    PENDING,
    /** Claimed in a short DB transaction; stale claims are eligible for recovery after a lease. */
    PROCESSING,
    COMPLETED,
    // Exceeded max retry attempts — needs manual attention/alerting;
    // the scheduled processor stops touching it once it reaches this state.
    DEAD_LETTER
}
