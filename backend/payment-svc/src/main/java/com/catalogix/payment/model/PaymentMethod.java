package com.catalogix.payment.model;

// CARD and UPI both capture payment immediately (mock, same as before —
// see PaymentSvc for the decline conventions each uses). COD captures
// nothing now: the order is confirmed, but the money changes hands at
// delivery, tracked outside this system entirely — same as how Amazon/
// Flipkart's own systems treat a COD order as "Confirmed," not "Paid,"
// the moment it's placed.
public enum PaymentMethod {
    CARD,
    UPI,
    COD
}
