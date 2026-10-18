package com.catalogix.cart.dto;

import java.util.List;

public class CheckoutHandoff {
    private List<CartItemLine> items;

    public CheckoutHandoff() {}
    public CheckoutHandoff(List<CartItemLine> items) { this.items = items; }
    public List<CartItemLine> getItems() { return items; }
    public void setItems(List<CartItemLine> items) { this.items = items; }
}
