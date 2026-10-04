package com.catalogix.checkout.model;

// Mirrors payment-svc's PaymentMethod — services don't share code across
// the boundary, same convention as OrderStatus/PaymentStatus each having
// their own copy where a concept genuinely exists in both places.
public enum PaymentMethod {
    CARD,
    UPI,
    COD
}
