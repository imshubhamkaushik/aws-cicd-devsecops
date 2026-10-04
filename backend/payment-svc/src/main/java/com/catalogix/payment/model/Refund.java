package com.catalogix.payment.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A refund against a previously-successful CARD/UPI payment. Kept as its
 * own table rather than another row in payments — a refund isn't a payment
 * attempt, it's a reversal of one, and giving it its own shape avoids
 * overloading Payment.status with a meaning ("this row is money coming
 * back, not going out") the rest of that entity was never designed to carry.
 *
 * COD orders never reach here: see checkout-svc's cancellation/refund flow, which skips
 * calling this endpoint entirely for a COD order (nothing was ever
 * captured, so there's nothing to reverse).
 */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    // The original successful Payment row this refund reverses.
    @Column(name = "original_payment_id", nullable = false)
    private Long originalPaymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String reference;

    // Caller-supplied replay key; unique per order (see V4). Null for callers that send none.
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Refund() {}

    public Refund(Long orderId, Long originalPaymentId, BigDecimal amount, String reference) {
        this.orderId = orderId;
        this.originalPaymentId = originalPaymentId;
        this.amount = amount;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(Long originalPaymentId) { this.originalPaymentId = originalPaymentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
