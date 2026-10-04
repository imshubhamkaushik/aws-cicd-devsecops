package com.catalogix.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    public ForgotPasswordRequest() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through the setters after construction.
         */
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
