package com.catalogix.user.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshRequest {

    @NotBlank(message = "refreshToken is required")
    private String refreshToken;

    public RefreshRequest() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through the setters after construction.
         */
    }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
