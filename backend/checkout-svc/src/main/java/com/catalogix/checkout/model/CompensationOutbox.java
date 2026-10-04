package com.catalogix.checkout.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Queue of compensating actions (currently releasing reserved stock in inventory-svc) that must
 * reach a downstream service eventually even if they cannot happen now. Written in the same
 * transaction as the local state change, or independently when that transaction must roll back
 * after a remote side effect has already committed.
 */
@Entity
@Table(name = "compensation_outbox")
public class CompensationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationType type;

    @Column(name = "product_id")
    private Long productId;

    @Column
    private Integer delta;

    // RELEASE_STOCK only: idempotency keys for inventory-svc.
    @Column(name = "operation_id", length = 120)
    private String operationId;

    @Column(name = "undo_of", length = 120)
    private String undoOf;


    @Column(length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CompensationOutbox() {
        /*
         * Required by JPA for entity instantiation. Fields are populated by
         * Hibernate or by the static factory methods after construction.
         */
    }

    public static CompensationOutbox releaseStock(Long productId, Integer delta, String reason) {
        CompensationOutbox e = new CompensationOutbox();
        e.type = CompensationType.RELEASE_STOCK;
        e.productId = productId;
        e.delta = delta;
        e.reason = reason;
        return e;
    }

    public static CompensationOutbox releaseStock(Long productId, Integer delta, String reason,
                                                  String operationId, String undoOf) {
        CompensationOutbox e = releaseStock(productId, delta, reason);
        e.operationId = operationId;
        e.undoOf = undoOf;
        return e;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getUndoOf() {
        return undoOf;
    }



    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CompensationType getType() { return type; }
    public void setType(CompensationType type) { this.type = type; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getDelta() { return delta; }
    public void setDelta(Integer delta) { this.delta = delta; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
