package com.catalogix.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateCartItemRequest {
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1 (use DELETE to remove the item instead)")
    private Integer quantity;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
