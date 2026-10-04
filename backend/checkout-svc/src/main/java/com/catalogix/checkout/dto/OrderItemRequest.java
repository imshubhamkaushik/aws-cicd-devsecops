package com.catalogix.checkout.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class OrderItemRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    public OrderItemRequest() {
        /*
        * Required by Jackson to instantiate this DTO during JSON deserialization.
        * Fields are populated through the setters after construction.
        */
    }

    public Long getProductId() {
        return productId;
    }
    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
