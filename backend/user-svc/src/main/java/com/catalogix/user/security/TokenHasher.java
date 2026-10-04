package com.catalogix.user.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Shared by every "long random opaque token, stored only as a hash" pattern
 * in this service: refresh tokens, email verification links, password reset
 * links. Keeps the generation/hashing scheme consistent across all of them.
 */
@Component
public class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
