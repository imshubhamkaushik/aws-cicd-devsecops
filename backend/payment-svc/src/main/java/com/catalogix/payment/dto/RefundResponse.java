package com.catalogix.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class RefundResponse {
    private Long id;
    private Long orderId;
    private BigDecimal amount;
    private String reference;
    private Instant createdAt;

    public RefundResponse() {}

    public RefundResponse(Long id, Long orderId, BigDecimal amount, String reference, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
