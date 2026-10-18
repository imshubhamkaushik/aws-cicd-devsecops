package com.catalogix.checkout.dto;

// Snapshot of the delivery address as it was at order-placement time — see
// Order's Javadoc. null on OrderResponse for any order placed without an
// addressId (or from before this feature existed).
public record ShippingAddressSummary(
        String label, String line1, String line2,
        String city, String state, String pincode, String phone) {}
