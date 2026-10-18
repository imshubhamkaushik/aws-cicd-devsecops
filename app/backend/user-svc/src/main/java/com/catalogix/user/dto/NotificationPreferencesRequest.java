package com.catalogix.user.dto;

// Both fields are required: the endpoint always carries the full state of both toggles.
public class NotificationPreferencesRequest {

    private boolean orderEmailsEnabled;

    public NotificationPreferencesRequest() {
        // Default constructor for serialization/deserialization
    }

    public boolean isOrderEmailsEnabled() {
        return orderEmailsEnabled;
    }

    public void setOrderEmailsEnabled(boolean orderEmailsEnabled) {
        this.orderEmailsEnabled = orderEmailsEnabled;
    }

}
