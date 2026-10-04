package com.catalogix.checkout.dto;

import java.math.BigDecimal;

public class OrderItemResponse {
    private Long productId;
    private String productName;
    private Long sellerId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public OrderItemResponse() {
        /*
         * Required by Jackson for DTO deserialization. Fields are populated
         * through setters after construction.
         */
    }

    public OrderItemResponse(Long productId, String productName, Integer quantity,
                              BigDecimal unitPrice, BigDecimal subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public Long getProductId() {
        return productId;
    }
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }
    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }

    public Integer getQuantity() {
        return quantity;
    }
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
