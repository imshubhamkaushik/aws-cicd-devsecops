package com.catalogix.cart.client;

import java.math.BigDecimal;

// Mirrors the fields of catalog-svc's ProductResponse that cart-svc actually
// needs — intentionally not the full response shape, so a field catalog-svc
// adds later doesn't require touching this service.
public class ProductInfo {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stockQuantity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
}
