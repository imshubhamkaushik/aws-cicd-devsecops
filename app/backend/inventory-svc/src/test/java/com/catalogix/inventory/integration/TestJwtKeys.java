package com.catalogix.inventory.integration;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Per-test-process ephemeral RSA keys. No production credential is reused by tests. */
final class TestJwtKeys {
    private static final KeyPair USER_PAIR = generate();
    private static final KeyPair INTERNAL_PAIR = generate();

    private TestJwtKeys() { }

    /** currentService is kept so call sites read the same; the internal key is shared by all services. */
    static void register(DynamicPropertyRegistry registry, String currentService) {
        registry.add("JWT_PRIVATE_KEY_B64", () -> encode(USER_PAIR.getPrivate()));
        registry.add("JWT_PUBLIC_KEY_B64", () -> encode(USER_PAIR.getPublic()));
        registry.add("INTERNAL_JWT_PRIVATE_KEY_B64", () -> encode(INTERNAL_PAIR.getPrivate()));
        registry.add("INTERNAL_JWT_PUBLIC_KEY_B64", () -> encode(INTERNAL_PAIR.getPublic()));
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String encode(java.security.Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
