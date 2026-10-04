package com.catalogix.checkout.dto;

// The request body is optional; addressId may be omitted.
public class CheckoutFromCartRequest {

    private Long addressId;

    public CheckoutFromCartRequest() {
        /*
        * Required by Jackson for JSON deserialization. The request body is optional,
        * so Spring may also construct this DTO when the endpoint is invoked without
        * an addressId.
        */
    }

    public Long getAddressId() { return addressId; }
    public void setAddressId(Long addressId) { this.addressId = addressId; }
}
