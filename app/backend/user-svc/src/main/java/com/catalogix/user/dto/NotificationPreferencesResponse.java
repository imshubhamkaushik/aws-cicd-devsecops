package com.catalogix.user.dto;

// Minimal payload for the SYSTEM-only preferences lookup used by notification-svc,
// so no profile data is exposed to the caller.
public class NotificationPreferencesResponse {

    private boolean orderEmailsEnabled;

    public NotificationPreferencesResponse() {
    }

    public NotificationPreferencesResponse(boolean orderEmailsEnabled) {
        this.orderEmailsEnabled = orderEmailsEnabled;
    }

    public boolean isOrderEmailsEnabled() {
        return orderEmailsEnabled;
    }

    public void setOrderEmailsEnabled(boolean orderEmailsEnabled) {
        this.orderEmailsEnabled = orderEmailsEnabled;
    }

}
