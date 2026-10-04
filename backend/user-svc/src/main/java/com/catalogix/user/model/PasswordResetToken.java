package com.catalogix.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends AbstractSingleUseToken {

    public PasswordResetToken() {
        super();
    }

    public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
        super(userId, tokenHash, expiresAt);
    }
}
