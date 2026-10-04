package com.catalogix.user.dto;

import java.time.Instant;

// Never exposes the token hash; this DTO only lets a user list and revoke their own sessions.
public class SessionResponse {

    private Long id;
    private String userAgent;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    // True for the session tied to this request's refresh cookie, so the UI can mark it
    // "This device" and leave revoking it to the regular log-out.
    private boolean current;

    public SessionResponse() {}

    public SessionResponse(Long id, String userAgent, Instant createdAt, Instant lastUsedAt,
                            Instant expiresAt, boolean current) {
        this.id = id;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.expiresAt = expiresAt;
        this.current = current;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }
}
