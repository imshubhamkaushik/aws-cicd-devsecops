package com.catalogix.notification.event;

import java.time.Instant;

public record EmailVerificationRequestedEvent(
        String userEmail, String userName, String verificationLink, Instant occurredAt
) {
}
