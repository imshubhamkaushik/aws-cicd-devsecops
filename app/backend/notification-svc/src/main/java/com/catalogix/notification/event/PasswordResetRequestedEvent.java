package com.catalogix.notification.event;

import java.time.Instant;

public record PasswordResetRequestedEvent(
        String userEmail, String userName, String resetLink, Instant occurredAt
) {
}
