package com.catalogix.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One processed stock adjustment, keyed by the caller's operation id. See V2__inventory_operations.sql. */
@Entity
@Table(name = "inventory_operations")
public class InventoryOperation {

    @Id
    @Column(name = "operation_id", length = 120)
    private String operationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int delta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public InventoryOperation() {
        /* required by JPA */
    }

    public InventoryOperation(String operationId, Long productId, int delta) {
        this.operationId = operationId;
        this.productId = productId;
        this.delta = delta;
    }

    public String getOperationId() { return operationId; }
    public Long getProductId() { return productId; }
    public int getDelta() { return delta; }
    public Instant getCreatedAt() { return createdAt; }
}
