package com.catalogix.checkout.exception;

// Raised when an operation doesn't make sense for the order's current status
// — e.g. paying an already-CONFIRMED order, or cancelling a SHIPPED one.
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
