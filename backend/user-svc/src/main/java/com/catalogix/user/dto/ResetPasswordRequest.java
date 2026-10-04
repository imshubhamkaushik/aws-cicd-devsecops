package com.catalogix.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ResetPasswordRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "newPassword is required")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,}$",
        message = "newPassword must be at least 6 characters and include a letter and a number"
    )
    private String newPassword;

    public ResetPasswordRequest() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through the setters after construction.
         */
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
