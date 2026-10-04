package com.catalogix.cart.dto;

import java.math.BigDecimal;

public class CartItemResponse {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private Integer availableStock;

    public CartItemResponse() {}
    public CartItemResponse(Long productId, String productName, Integer quantity,
                             BigDecimal unitPrice, BigDecimal subtotal, Integer availableStock) {
        this.productId = productId; this.productName = productName; this.quantity = quantity;
        this.unitPrice = unitPrice; this.subtotal = subtotal; this.availableStock = availableStock;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
}
