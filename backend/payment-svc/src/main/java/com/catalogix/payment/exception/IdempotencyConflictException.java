package com.catalogix.payment.exception;

/**
 * The same idempotency key was reused for a different payment operation.
 * Clients must generate a fresh key when changing the logical request.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
