package com.catalogix.payment.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single payment attempt against an order. One row per attempt. Owned
 * entirely by payment-svc: no other service touches this table directly,
 * mirroring the PCI-style isolation a real payment boundary needs even
 * though nothing here is a real card network.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to checkout-svc's orders table — NOT an FK constraint, since
    // that table now lives in a different database. Referential integrity
    // across this boundary is checkout-svc's job.
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    // Client-generated key that makes payment retries safe. Null preserves
    // backwards compatibility for non-idempotent internal callers; the web
    // checkout supplies a UUID on every payment attempt.
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Payment() {
    }

    public Payment(Long orderId, Long requestedByUserId, BigDecimal amount, PaymentMethod method,
                   PaymentStatus status, String reference) {
        this.orderId = orderId;
        this.requestedByUserId = requestedByUserId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.reference = reference;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
