package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderResponse {

    private Long id;
    private Long userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Instant createdAt;
    private List<OrderItemResponse> items;
    private ShippingAddressSummary shippingAddress;

    public OrderResponse() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through setters after construction.
         */
    }

    public OrderResponse(
            Long id,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            Instant createdAt,
            List<OrderItemResponse> items) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.items = items;
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

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }



    public ShippingAddressSummary getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(ShippingAddressSummary shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}