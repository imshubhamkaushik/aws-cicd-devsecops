package com.catalogix.checkout.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.CascadeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Id of the user (from user-svc) who placed this order.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    // When the latest payment attempt started. Lets PendingOrderExpiryJob find claims orphaned by a
    // crash and gives an interrupted customer a fresh payment window.
    @Column(name = "payment_started_at")
    private Instant paymentStartedAt;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Optional Idempotency-Key header value; a repeat for the same user returns the original order.
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;


    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    // Status timeline, loaded lazily because only the tracking endpoint needs it.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderStatusEvent> statusEvents = new ArrayList<>();

    // Shipping address snapshot taken when the order was placed, not a live foreign key, so later
    // edits to the address book never change an existing order. Null when no address was supplied.
    @Column(name = "shipping_label", length = 40)
    private String shippingLabel;

    @Column(name = "shipping_line1", length = 200)
    private String shippingLine1;

    @Column(name = "shipping_line2", length = 200)
    private String shippingLine2;

    @Column(name = "shipping_city", length = 100)
    private String shippingCity;

    @Column(name = "shipping_state", length = 100)
    private String shippingState;

    @Column(name = "shipping_pincode", length = 12)
    private String shippingPincode;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    // Set when payment succeeds (or COD is confirmed) so later reconciliation need not call payment-svc.
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    // Snapshotted at payment time so the invoice has a customer identity to print.
    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    public Order() {
        // Required by JPA for entity instantiation.
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OrderStatus getStatus() {
        return status;
    }
    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }


    // Convenience method to keep both sides of the bidirectional association in sync.
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public List<OrderStatusEvent> getStatusEvents() {
        return statusEvents;
    }
    public void setStatusEvents(List<OrderStatusEvent> statusEvents) {
        this.statusEvents = statusEvents;
    }

    // Appends a timeline entry and keeps both sides of the association in sync.
    public void addStatusEvent(OrderStatus status, String note) {
        OrderStatusEvent event = new OrderStatusEvent(status, note);
        statusEvents.add(event);
        event.setOrder(this);
    }

    public String getShippingLabel() { return shippingLabel; }
    public void setShippingLabel(String shippingLabel) { this.shippingLabel = shippingLabel; }

    public String getShippingLine1() { return shippingLine1; }
    public void setShippingLine1(String shippingLine1) { this.shippingLine1 = shippingLine1; }

    public String getShippingLine2() { return shippingLine2; }
    public void setShippingLine2(String shippingLine2) { this.shippingLine2 = shippingLine2; }

    public String getShippingCity() { return shippingCity; }
    public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }

    public String getShippingState() { return shippingState; }
    public void setShippingState(String shippingState) { this.shippingState = shippingState; }

    public String getShippingPincode() { return shippingPincode; }
    public void setShippingPincode(String shippingPincode) { this.shippingPincode = shippingPincode; }

    public String getShippingPhone() { return shippingPhone; }
    public void setShippingPhone(String shippingPhone) { this.shippingPhone = shippingPhone; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public Instant getPaymentStartedAt() {
        return paymentStartedAt;
    }

    public void setPaymentStartedAt(Instant paymentStartedAt) {
        this.paymentStartedAt = paymentStartedAt;
    }
}
