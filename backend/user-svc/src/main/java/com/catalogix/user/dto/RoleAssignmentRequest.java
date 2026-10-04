package com.catalogix.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body of PUT /users/{id}/role — an admin assigning a role to a user. */
public class RoleAssignmentRequest {

    @NotBlank(message = "role is required")
    @Pattern(regexp = "USER|SELLER|ADMIN", message = "role must be one of USER, SELLER, ADMIN")
    private String role;

    public RoleAssignmentRequest() {}

    public RoleAssignmentRequest(String role) {
        this.role = role;
    }

    public String getRole() { return role; }

    public void setRole(String role) { this.role = role; }
}
