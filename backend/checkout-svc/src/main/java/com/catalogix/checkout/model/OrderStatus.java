package com.catalogix.checkout.model;

// Lifecycle: PENDING_PAYMENT -> (PAYMENT_PROCESSING) -> CONFIRMED -> SHIPPED -> DELIVERED
// CANCELLED is reachable from PENDING_PAYMENT or CONFIRMED only — once an
// order has shipped, "cancelling" it is a returns/refunds problem, which is
// out of scope here.
//
// PAYMENT_PROCESSING is a short-lived claim held while checkout-svc waits for payment-svc.
// The status itself is the mutual exclusion, so a second pay/cancel attempt is rejected
// immediately instead of holding a row lock and a DB connection across the HTTP call.
// It always ends in CONFIRMED / CANCELLED, or back in PENDING_PAYMENT if the
// outcome was unknown (see CheckoutSvc#payOrder and PendingOrderExpiryJob).
public enum OrderStatus {
    PENDING_PAYMENT,
    PAYMENT_PROCESSING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
