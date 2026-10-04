package com.catalogix.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken extends AbstractSingleUseToken {

    public EmailVerificationToken() {
        super();
    }

    public EmailVerificationToken(Long userId, String tokenHash, Instant expiresAt) {
        super(userId, tokenHash, expiresAt);
    }
}
