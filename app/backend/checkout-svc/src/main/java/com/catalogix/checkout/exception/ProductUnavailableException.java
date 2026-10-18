package com.catalogix.checkout.exception;

// Raised when a line item cannot be fulfilled: the product doesn't exist or
// there isn't enough stock.
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String message) {
        super(message);
    }
}
