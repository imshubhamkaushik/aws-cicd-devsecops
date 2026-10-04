package com.catalogix.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Extends the shared com.catalogix.security.JwtService with the one thing
 * that's genuinely unique to user-svc: minting real, per-user access tokens
 * with a configurable expiry. Parsing (parseClaims) and system-token minting
 * (generateSystemToken) are inherited as-is.
 */
@Service
public class UserJwtService extends com.catalogix.security.JwtService {

    private final long expirationMs;

    public UserJwtService(
            @Value("${JWT_SECRET}") String secret,
            @Value("${JWT_SECRET_PREVIOUS:}") String previousSecret,
            @Value("${JWT_EXPIRATION_MS:900000}") long expirationMs
    ) {
        super(secret, previousSecret);
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim(Claims.ISSUED_AT, now.getEpochSecond())
                .claim(Claims.EXPIRATION, expiry.getEpochSecond())
                .signWith(getKey())
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}