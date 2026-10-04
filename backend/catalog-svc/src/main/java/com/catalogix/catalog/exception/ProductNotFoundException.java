package com.catalogix.catalog.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Product not found: " + id);
    }
}
