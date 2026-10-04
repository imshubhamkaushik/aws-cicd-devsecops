package com.catalogix.notification.dto;

import com.catalogix.notification.model.NotificationStatus;
import java.time.Instant;

public class NotificationLogResponse {
    private Long id;
    private String recipient;
    private String subject;
    private NotificationStatus status;
    private String error;
    private Instant createdAt;

    public NotificationLogResponse() {}

    public NotificationLogResponse(Long id, String recipient, String subject,
                                    NotificationStatus status, String error, Instant createdAt) {
        this.id = id;
        this.recipient = recipient;
        this.subject = subject;
        this.status = status;
        this.error = error;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
