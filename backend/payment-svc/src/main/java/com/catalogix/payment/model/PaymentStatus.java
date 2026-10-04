package com.catalogix.payment.model;

public enum PaymentStatus {
    SUCCEEDED,
    FAILED,
    // COD only: payment is deferred to delivery time, not captured now.
    // Treated as a successful order-confirmation outcome by checkout-svc
    // (see CheckoutSvc#payOrder) even though no money has moved yet.
    COD_PENDING
}
