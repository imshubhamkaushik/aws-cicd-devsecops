package com.catalogix.inventory.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(Long productId, int available, int requested) {
        super("Insufficient stock for product " + productId
                + ": available=" + available + ", requested=" + requested);
    }
}
