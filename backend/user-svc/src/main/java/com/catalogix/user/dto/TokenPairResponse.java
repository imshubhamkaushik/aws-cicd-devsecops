package com.catalogix.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

// Returned by POST /users/refresh. Lighter than AuthResponse because the profile is
// unchanged. refreshToken is @JsonIgnore'd and only used to rotate the cookie.
public class TokenPairResponse {
    private String accessToken;
    private long accessTokenExpiresInMs;
    private String refreshToken;

    public TokenPairResponse() {}

    public TokenPairResponse(String accessToken, long accessTokenExpiresInMs, String refreshToken) {
        this.accessToken = accessToken;
        this.accessTokenExpiresInMs = accessTokenExpiresInMs;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public long getAccessTokenExpiresInMs() { return accessTokenExpiresInMs; }
    public void setAccessTokenExpiresInMs(long accessTokenExpiresInMs) { this.accessTokenExpiresInMs = accessTokenExpiresInMs; }

    @JsonIgnore
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
