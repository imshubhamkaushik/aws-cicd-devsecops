package com.catalogix.user.model;

import jakarta.persistence.*;
import java.time.Instant;

// Only a SHA-256 hash of the token is stored, never the raw value.
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Device metadata shown in the session list; nullable.
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    // When the original sign-in happened. Copied unchanged on rotation so a session has an
    // absolute maximum age.
    @Column(name = "session_started_at")
    private Instant sessionStartedAt;

    public RefreshToken() {
    }

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.lastUsedAt = Instant.now();
    }

    public Instant getSessionStartedAt() {
        return sessionStartedAt;
    }
    public void setSessionStartedAt(Instant sessionStartedAt) {
        this.sessionStartedAt = sessionStartedAt;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }
    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUserAgent() {
        return userAgent;
    }
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public boolean isValid(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
