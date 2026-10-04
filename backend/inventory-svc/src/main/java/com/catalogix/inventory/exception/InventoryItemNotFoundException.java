package com.catalogix.inventory.exception;

public class InventoryItemNotFoundException extends RuntimeException {
    public InventoryItemNotFoundException(Long productId) {
        super("No stock record for product: " + productId);
    }
}
