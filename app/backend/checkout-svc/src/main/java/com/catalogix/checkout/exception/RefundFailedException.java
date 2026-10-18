package com.catalogix.checkout.exception;

// The refund call to payment-svc itself failed or errored — kept distinct
// from InvalidReturnException so it maps to a different, more accurate
// status code (502-ish "the downstream call failed" vs 400 "this request
// doesn't make sense").
public class RefundFailedException extends RuntimeException {
    public RefundFailedException(String message) {
        super(message);
    }
}
