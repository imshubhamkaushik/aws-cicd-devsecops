package com.catalogix.inventory.exception;

/** The same operation id was reused for a different inventory adjustment. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
