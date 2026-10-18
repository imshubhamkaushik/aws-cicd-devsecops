package com.catalogix.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public class CartResponse {
    private List<CartItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal total;

    public CartResponse() {}
    public CartResponse(List<CartItemResponse> items, BigDecimal subtotal, BigDecimal total) {
        this.items = items;
        this.subtotal = subtotal;
        this.total = total;
    }

    public List<CartItemResponse> getItems() { return items; }
    public void setItems(List<CartItemResponse> items) { this.items = items; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}
