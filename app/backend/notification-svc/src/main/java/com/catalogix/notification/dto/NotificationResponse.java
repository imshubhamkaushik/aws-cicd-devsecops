package com.catalogix.notification.dto;

import com.catalogix.notification.model.NotificationStatus;

public class NotificationResponse {
    private Long id;
    private NotificationStatus status;
    private String error;

    public NotificationResponse() {
    }

    public NotificationResponse(Long id, NotificationStatus status, String error) {
        this.id = id;
        this.status = status;
        this.error = error;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
