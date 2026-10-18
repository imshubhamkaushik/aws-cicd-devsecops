package com.catalogix.inventory.dto;

public class InventoryResponse {
    private Long productId;
    private int quantity;

    public InventoryResponse() {}
    public InventoryResponse(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
