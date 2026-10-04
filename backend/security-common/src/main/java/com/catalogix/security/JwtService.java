package com.catalogix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Verifies HS256 JWTs issued by user-svc (real user sessions) or minted as
 * short-lived "system" tokens by any service that needs to make an
 * authenticated call with no inbound user request to forward a token from
 * (e.g. catalog-svc calling inventory-svc's /adjust endpoint, or
 * checkout-svc's background compensation outbox processor).
 *
 * Shared JWT verification and system-token support live here so all backend
 * services use the same security behavior and token conventions. User-specific
 * token issuance remains in user-svc, which extends this class.
 *
 * MUST be configured with the same JWT_SECRET as every other service —
 * HS256/HMAC is symmetric, so anyone holding the secret can both sign and
 * verify. No service needs to know or care which other service minted a
 * given token, only that its signature is valid.
 *
 * KEY ROTATION: set JWT_SECRET_PREVIOUS to the old secret while rolling out a
 * new JWT_SECRET. New tokens are signed with JWT_SECRET only; tokens signed with
 * the previous one are still accepted, so sessions survive the rotation. Remove
 * JWT_SECRET_PREVIOUS after the longest token lifetime (the access-token TTL)
 * has passed. Without it, changing JWT_SECRET invalidates every live token at once.
 */
@Service
public class JwtService {

    private final SecretKey key;
    /** Verify-only key for the secret being rotated out; null when not rotating. */
    private final SecretKey previousKey;

    private static final String EXAMPLE_PLACEHOLDER =
            "change-this-to-a-long-random-string-at-least-32-chars";

    // Sentinel subject/role/email for internally-minted tokens — never a real
    // user id. Fixed, not per-service, deliberately: the receiving service
    // only checks role == SYSTEM, never which service minted the token.
    private static final String SYSTEM_SUBJECT = "0";
    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final String SYSTEM_EMAIL = "system@internal";
    private static final Duration SYSTEM_TOKEN_TTL = Duration.ofMinutes(5); // 5 minutes

    @Autowired
    public JwtService(
            @Value("${JWT_SECRET}") String secret,
            @Value("${JWT_SECRET_PREVIOUS:}") String previousSecret) {
        this.key = buildKey(secret, "JWT_SECRET");
        this.previousKey = (previousSecret == null || previousSecret.isBlank())
                ? null
                : buildKey(previousSecret, "JWT_SECRET_PREVIOUS");
    }

    /** No key rotation in progress. */
    public JwtService(String secret) {
        this(secret, "");
    }

    private static SecretKey buildKey(String secret, String propertyName) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                propertyName + " must be set and at least 32 characters long (HS256 requires a 256-bit key)");
        }
        if (EXAMPLE_PLACEHOLDER.equals(secret)) {
            throw new IllegalStateException(
                propertyName + " is still set to the placeholder value from .env.example — "
                + "generate a real one, e.g. `openssl rand -base64 48`");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws JwtException if the token is malformed, expired, or the signature does not match.
     */
    public Claims parseClaims(String token) {
        try {
            return parseWith(key, token);
        } catch (SignatureException e) {
            // Only a BAD SIGNATURE falls through to the previous key — an expired
            // or malformed token is rejected as usual.
            if (previousKey == null) {
                throw e;
            }
            return parseWith(previousKey, token);
        }
    }

    private static Claims parseWith(SecretKey verificationKey, String token) {
        return Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Mints a short-lived token identifying this call as coming from the system itself, not a user. */
    public String generateSystemToken() {
        Instant now = Instant.now();
        Instant expiry = now.plus(SYSTEM_TOKEN_TTL);
        return Jwts.builder()
                .subject(SYSTEM_SUBJECT)
                .claim("email", SYSTEM_EMAIL)
                .claim("role", SYSTEM_ROLE)
                .claim(Claims.ISSUED_AT, now.getEpochSecond())
                .claim(Claims.EXPIRATION, expiry.getEpochSecond())
                .signWith(key)
                .compact();
    }

    /**
     * Exposed so user-svc's subclass can mint real, per-user tokens with the
     * same key without this class needing to know anything about users,
     * emails, or roles beyond the generic system sentinel above.
     */
    protected SecretKey getKey() {
        return key;
    }
}
