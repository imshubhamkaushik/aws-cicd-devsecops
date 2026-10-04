package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.CompensationType;
import com.catalogix.checkout.model.OutboxStatus;

import java.time.Instant;

public class OutboxEntryResponse {

    private Long id;
    private CompensationType type;
    private Long productId;
    private Integer delta;
    private String reason;
    private OutboxStatus status;
    private int attempts;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;

    public OutboxEntryResponse() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through setters after construction.
         */
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompensationType getType() {
        return type;
    }

    public void setType(CompensationType type) {
        this.type = type;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }


    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void setStatus(OutboxStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}