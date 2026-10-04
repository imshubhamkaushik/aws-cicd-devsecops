package com.catalogix.user.dto;

import java.time.Instant;

// DTO returned to client (never includes password).
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean verified;
    private Instant createdAt;
    private boolean orderEmailsEnabled;
    // Pending request for a higher role awaiting admin approval; null when none.
    private String requestedRole;

    public UserResponse() {
    }

    // Convenience constructor; verified defaults to false.
    public UserResponse(Long id, String name, String email, String role) {
        this(id, name, email, role, false);
    }

    // Convenience constructor; createdAt defaults to null and both preference flags to true.
    public UserResponse(
            Long id,
            String name,
            String email,
            String role,
            boolean verified
    ) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.verified = verified;
        this.createdAt = null;
        this.orderEmailsEnabled = true;
        this.requestedRole = null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isOrderEmailsEnabled() {
        return orderEmailsEnabled;
    }

    public void setOrderEmailsEnabled(boolean orderEmailsEnabled) {
        this.orderEmailsEnabled = orderEmailsEnabled;
    }


    public String getRequestedRole() {
        return requestedRole;
    }

    public void setRequestedRole(String requestedRole) {
        this.requestedRole = requestedRole;
    }
}