package com.catalogix.user.security;

import com.catalogix.security.JwtService;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Locale;

/**
 * User-facing access-token issuer.
 * Only this service receives JWT_PRIVATE_KEY_B64.
 */
@Service
@Primary
public class UserJwtService extends JwtService {

    private static final String USER_TOKEN_KID = "user-access-v1";
    private static final String USER_ISSUER = "user-svc";
    private static final String USER_AUDIENCE = "catalogix-api";

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String CLAIM_EXPIRATION = "exp";

    private static final String TOKEN_TYPE_ACCESS = "access";

    private static final long MIN_EXPIRATION_MS = 60_000L;
    private static final long MAX_EXPIRATION_MS = 86_400_000L;

    private final PrivateKey userSigningKey;
    private final long expirationMs;

    public UserJwtService(
            @Value("${JWT_PRIVATE_KEY_B64}") String userPrivateKeyB64,
            @Value("${JWT_PUBLIC_KEY_B64}") String userPublicKeyB64,
            @Value("${INTERNAL_JWT_PRIVATE_KEY_B64}") String internalPrivateKeyB64,
            @Value("${INTERNAL_JWT_PUBLIC_KEY_B64}") String internalPublicKeyB64,
            @Value("${spring.application.name:user-svc}") String serviceName,
            @Value("${JWT_EXPIRATION_MS:900000}") long expirationMs) {

        super(
                userPublicKeyB64,
                internalPrivateKeyB64,
                internalPublicKeyB64,
                serviceName);

        this.userSigningKey = parsePrivateKey(
                userPrivateKeyB64,
                "JWT_PRIVATE_KEY_B64");

        if (expirationMs < MIN_EXPIRATION_MS
                || expirationMs > MAX_EXPIRATION_MS) {
            throw new IllegalStateException(
                    "JWT_EXPIRATION_MS must be between 60 seconds and 24 hours");
        }

        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String email, String role) {
        if (userId == null || userId <= 0
                || email == null || email.isBlank()
                || !isValidRole(role)) {
            throw new IllegalArgumentException(
                    "Invalid user identity or role for access token");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(expirationMs);

        return Jwts.builder()
                .header()
                .keyId(USER_TOKEN_KID)
                .and()
                .issuer(USER_ISSUER)
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.toUpperCase(Locale.ROOT))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim("aud", USER_AUDIENCE)
                // JWT NumericDate values are expressed in epoch seconds.
                .claim(CLAIM_ISSUED_AT, issuedAt.getEpochSecond())
                .claim(CLAIM_EXPIRATION, expiresAt.getEpochSecond())
                .signWith(userSigningKey, Jwts.SIG.RS256)
                .compact();
    }

    private static boolean isValidRole(String role) {
        return role != null
                && (role.equalsIgnoreCase("USER")
                        || role.equalsIgnoreCase("SELLER")
                        || role.equalsIgnoreCase("ADMIN"));
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}