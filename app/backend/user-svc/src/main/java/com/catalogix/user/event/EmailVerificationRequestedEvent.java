package com.catalogix.user.event;

import java.time.Instant;

/**
 * Published whenever a verification link must be sent (registration, email change, resend).
 * Relayed to RabbitMQ only after the DB transaction commits (see UserEventPublisher);
 * notification-svc builds the actual email from this data.
 */
public record EmailVerificationRequestedEvent(
        String userEmail, String userName, String verificationLink, Instant occurredAt
) {
    public EmailVerificationRequestedEvent(String userEmail, String userName, String verificationLink) {
        this(userEmail, userName, verificationLink, Instant.now());
    }
}
