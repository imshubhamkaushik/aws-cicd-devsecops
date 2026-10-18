package com.catalogix.security;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/** Ephemeral RSA fixtures for security tests; never used by production configuration. */
final class JwtTestKeys {
    private static final KeyPair USER = generate();
    private static final KeyPair INTERNAL = generate();

    private JwtTestKeys() { }

    static JwtService service(String serviceName) {
        return new JwtService(encode(USER.getPublic()), encode(INTERNAL.getPrivate()),
                encode(INTERNAL.getPublic()), serviceName);
    }

    static JwtService serviceFromPem(String serviceName) {
        return new JwtService(encodePem(USER.getPublic(), "PUBLIC KEY"),
                encodePem(INTERNAL.getPrivate(), "PRIVATE KEY"),
                encodePem(INTERNAL.getPublic(), "PUBLIC KEY"), serviceName);
    }

    static String userPublicKey() { return encode(USER.getPublic()); }
    static String userPrivateKey() { return encode(USER.getPrivate()); }

    static String userAccessToken(long userId, String role, String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId("user-access-v1").and()
                .issuer("user-svc")
                .subject(Long.toString(userId))
                .claim("email", "test@example.com")
                .claim("role", role)
                .claim("token_type", "access")
                .claim("aud", audience)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(USER.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    static String rogueServiceToken(String issuer, String audience) {
        KeyPair rogue = generate();
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId("internal-v1").and()
                .issuer(issuer).subject(issuer)
                .claim("role", "SYSTEM")
                .claim("token_type", "service")
                .claim("aud", audience)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(120)))
                .signWith(rogue.getPrivate(), Jwts.SIG.RS256)
                .compact();
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

    private static String encodePem(java.security.Key key, String label) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded());
        String pem = "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }
}
