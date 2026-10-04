package com.catalogix.cart.exception;

// Raised when catalog-svc/inventory-svc can't answer for a product: it
// doesn't exist, or the lookup itself failed (including circuit-open).
public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(String message) {
        super(message);
    }
}
