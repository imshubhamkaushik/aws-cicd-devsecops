package com.catalogix.payment.exception;

public class DeclinedException extends RuntimeException {
    public DeclinedException(String message) {
        super(message);
    }
}
