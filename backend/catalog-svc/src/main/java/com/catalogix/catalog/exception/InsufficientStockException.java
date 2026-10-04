package com.catalogix.catalog.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(Long productId, int available, int requested) {
        super("Insufficient stock for product " + productId
                + ": available=" + available + ", requested=" + requested);
    }
}
