package com.catalogix.checkout.dto;

import com.catalogix.checkout.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    public UpdateOrderStatusRequest() {
        /*
        * Required by Jackson to instantiate this DTO during JSON deserialization.
        * Fields are populated through the setters after construction.
        */
    }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
}
