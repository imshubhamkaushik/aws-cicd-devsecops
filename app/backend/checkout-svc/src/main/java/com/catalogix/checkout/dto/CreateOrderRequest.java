package com.catalogix.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CreateOrderRequest {

    @NotEmpty(message = "An order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;


    // Optional for now: id of an address from the caller's own user-svc
    // address book (see AddressClient). If given, its fields are snapshotted
    // onto the order at placement time. Not yet @NotNull so existing
    // API consumers that don't send one keep working — tighten this once
    // the frontend has an address-selection step in the checkout flow.
    private Long addressId;

    public CreateOrderRequest() {
        /*
        * Required by Jackson to instantiate this DTO during JSON deserialization.
        * Fields are populated through the setters after construction.
        */
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }
    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }


    public Long getAddressId() {
        return addressId;
    }
    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }
}
