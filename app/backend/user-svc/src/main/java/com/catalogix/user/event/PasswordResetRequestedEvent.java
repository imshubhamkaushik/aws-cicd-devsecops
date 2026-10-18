package com.catalogix.user.event;

import java.time.Instant;

public record PasswordResetRequestedEvent(
        String userEmail, String userName, String resetLink, Instant occurredAt
) {
    public PasswordResetRequestedEvent(String userEmail, String userName, String resetLink) {
        this(userEmail, userName, resetLink, Instant.now());
    }
}
