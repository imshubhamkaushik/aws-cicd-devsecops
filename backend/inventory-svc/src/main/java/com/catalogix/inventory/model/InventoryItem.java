package com.catalogix.inventory.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One row per product. productId is the primary key here on purpose —
 * inventory is a 1:1 satellite of catalog-svc's product, not an independent
 * entity with its own identity. There is deliberately NO foreign key
 * constraint to catalog-svc's products table: it lives in a different
 * database now, so referential integrity across that boundary is enforced
 * by catalog-svc only ever creating a row here right after it creates the
 * product (see InventorySvc#initialize and catalog-svc's InventoryClient).
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public InventoryItem() {}

    public InventoryItem(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
