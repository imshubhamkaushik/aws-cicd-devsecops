package com.catalogix.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

// Returned by /users/register, /users/login and /users/refresh.
// accessToken is the short-lived JWT sent as "Authorization: Bearer <token>".
// refreshToken is @JsonIgnore'd and never appears in the JSON body; UserController reads it
// only to set an httpOnly cookie, so an XSS bug in the SPA cannot exfiltrate the long-lived credential.
public class AuthResponse {

    private String accessToken;
    private long accessTokenExpiresInMs;
    private String refreshToken;
    private UserResponse user;

    public AuthResponse() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through the setters after construction.
         */
    }

    public AuthResponse(String accessToken, long accessTokenExpiresInMs, String refreshToken, UserResponse user) {
        this.accessToken = accessToken;
        this.accessTokenExpiresInMs = accessTokenExpiresInMs;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public long getAccessTokenExpiresInMs() { return accessTokenExpiresInMs; }
    public void setAccessTokenExpiresInMs(long accessTokenExpiresInMs) { this.accessTokenExpiresInMs = accessTokenExpiresInMs; }

    @JsonIgnore
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public UserResponse getUser() { return user; }
    public void setUser(UserResponse user) { this.user = user; }
}
