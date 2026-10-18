package com.catalogix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT verification shared by backend services. End-user access tokens are
 * signed only by
 * user-svc (RS256); other services receive only the verification key.
 * Service-to-service
 * tokens (no user involved, e.g. checkout-svc calling payment-svc) are signed with ONE shared
 * internal RS256 key pair that every service holds, name the calling service in the issuer claim,
 * and are audience-restricted to one receiving service.
 *
 * Trade-off, on purpose: because all services share the internal key, the issuer claim is a
 * sanity check, not proof. A compromised service could sign as another one. Per-service keys (or
 * mTLS) would close that gap; a Kubernetes NetworkPolicy limits who can reach internal services.
 *
 * Key configuration uses Base64-encoded DER: user JWT_PRIVATE_KEY_B64 (PKCS#8)
 * is present only
 * in user-svc; JWT_PUBLIC_KEY_B64 (X.509 SubjectPublicKeyInfo) is distributed
 * to verifiers.
 * Every service gets INTERNAL_JWT_PRIVATE_KEY_B64 (signs) and INTERNAL_JWT_PUBLIC_KEY_B64 (verifies).
 */
@Service
public class JwtService {

    private static final Duration SERVICE_TOKEN_TTL = Duration.ofMinutes(2);

    private static final String USER_TOKEN_KID = "user-access-v1";
    private static final String INTERNAL_TOKEN_KID = "internal-v1";
    private static final String USER_ISSUER = "user-svc";
    private static final String USER_AUDIENCE = "catalogix-api";

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_SERVICE = "service";
    private static final String CLAIM_ROLE = "role";
    private static final String ROLE_SYSTEM = "SYSTEM";
    private static final String CLAIM_ISSUED_AT = "iat";
    private static final String CLAIM_EXPIRATION = "exp";
    private static final String PEM_BEGIN_MARKER = "-----BEGIN ";
    private static final String PEM_END_MARKER = "-----END ";

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,40}");
    private static final Pattern KID_PATTERN = Pattern.compile("\"kid\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ALG_PATTERN = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]+)\"");

    private final PublicKey userVerificationKey;
    private final PrivateKey internalSigningKey;
    private final PublicKey internalVerificationKey;
    private final String serviceName;

    public JwtService(
            @Value("${JWT_PUBLIC_KEY_B64}") String userPublicKeyB64,
            @Value("${INTERNAL_JWT_PRIVATE_KEY_B64}") String internalPrivateKeyB64,
            @Value("${INTERNAL_JWT_PUBLIC_KEY_B64}") String internalPublicKeyB64,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.userVerificationKey = parsePublicKey(userPublicKeyB64, "JWT_PUBLIC_KEY_B64");
        this.internalSigningKey = parsePrivateKey(
                internalPrivateKeyB64, "INTERNAL_JWT_PRIVATE_KEY_B64");
        this.internalVerificationKey = parsePublicKey(
                internalPublicKeyB64, "INTERNAL_JWT_PUBLIC_KEY_B64");

        if (serviceName == null || !SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            throw new IllegalStateException(
                    "spring.application.name must be a valid service identifier");
        }
        this.serviceName = serviceName;
    }

    /**
     * Parse and validate either a user access token or a service token scoped to
     * this service.
     */
    public Claims parseClaims(String token, String expectedAudience) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT must not be blank");
        }

        String kid = readHeaderValue(token, KID_PATTERN, "kid");
        String algorithm = readHeaderValue(token, ALG_PATTERN, "alg");
        if (!"RS256".equals(algorithm)) {
            throw new JwtException("Only RS256 tokens are accepted");
        }

        Claims claims;
        if (USER_TOKEN_KID.equals(kid)) {
            claims = parseWith(userVerificationKey, token);
            validateUserToken(claims);
        } else if (INTERNAL_TOKEN_KID.equals(kid)) {
            claims = parseWith(internalVerificationKey, token);
            validateServiceToken(claims, expectedAudience);
        } else {
            throw new JwtException("JWT key id is not trusted");
        }

        return claims;
    }

    private static Claims parseWith(PublicKey verificationKey, String token) {
        return Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static void validateUserToken(Claims claims) {
        if (!USER_ISSUER.equals(claims.getIssuer())
                || !TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))
                || !hasAudience(claims, USER_AUDIENCE)) {
            throw new JwtException("JWT is not a valid user access token");
        }

        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null || !(role.equalsIgnoreCase("USER")
                || role.equalsIgnoreCase("SELLER")
                || role.equalsIgnoreCase("ADMIN"))) {
            throw new JwtException("JWT contains an invalid user role");
        }

        String subject = claims.getSubject();
        if (subject == null || !subject.matches("[1-9]\\d*")) {
            throw new JwtException("JWT contains an invalid user subject");
        }
    }

    private static void validateServiceToken(Claims claims, String expectedAudience) {
        String issuer = claims.getIssuer();
        if (issuer == null
                || !SERVICE_NAME_PATTERN.matcher(issuer).matches()
                || !issuer.equals(claims.getSubject())
                || !TOKEN_TYPE_SERVICE.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))
                || !ROLE_SYSTEM.equals(claims.get(CLAIM_ROLE, String.class))
                || expectedAudience == null
                || !hasAudience(claims, expectedAudience)) {
            throw new JwtException("Service JWT issuer, role, or audience is invalid");
        }
    }

    private static boolean hasAudience(Claims claims, String expected) {
        Object value = claims.get("aud");
        if (value instanceof String audience) {
            return expected.equals(audience);
        }
        if (value instanceof Collection<?> audiences) {
            return audiences.stream().anyMatch(expected::equals);
        }
        return false;
    }

    /**
     * Issues a short-lived SYSTEM token for calling {@code targetService}. The issuer claim names
     * this service; the signature only proves the caller holds the shared internal key.
     */
    public String generateSystemToken(String targetService) {
        if (targetService == null
                || !SERVICE_NAME_PATTERN.matcher(targetService).matches()) {
            throw new IllegalArgumentException(
                    "A valid target service audience is required");
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SERVICE_TOKEN_TTL);

        return Jwts.builder()
                .header().keyId(INTERNAL_TOKEN_KID).and()
                .issuer(serviceName)
                .subject(serviceName)
                .claim("email", "system@internal")
                .claim(CLAIM_ROLE, ROLE_SYSTEM)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_SERVICE)
                .claim("aud", targetService)
                // JWT NumericDate values are epoch seconds, derived directly from Instant.
                .claim(CLAIM_ISSUED_AT, now.getEpochSecond())
                .claim(CLAIM_EXPIRATION, expiresAt.getEpochSecond())
                .signWith(internalSigningKey, Jwts.SIG.RS256)
                .compact();
    }

    protected static PrivateKey parsePrivateKey(String encoded, String propertyName) {
        try {
            byte[] der = decodeKeyMaterial(encoded, propertyName, true);
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!"RSA".equalsIgnoreCase(key.getAlgorithm())) {
                throw new IllegalStateException(
                        propertyName + " must contain an RSA PKCS#8 private key");
            }
            return key;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(
                    propertyName + " must be Base64-encoded PKCS#8 RSA key material", exception);
        }
    }

    private static PublicKey parsePublicKey(String encoded, String propertyName) {
        try {
            byte[] der = decodeKeyMaterial(encoded, propertyName, false);
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
            if (!"RSA".equalsIgnoreCase(key.getAlgorithm())) {
                throw new IllegalStateException(
                        propertyName + " must contain an RSA public key");
            }
            return key;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(
                    propertyName + " must be Base64-encoded X.509 RSA public key material",
                    exception);
        }
    }

    /** Accepts Base64-encoded DER and Base64-encoded PEM text. */
    private static byte[] decodeKeyMaterial(
            String encoded, String propertyName, boolean privateKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(required(encoded, propertyName));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    propertyName + " must be base64 encoded key material", exception);
        }

        String text = new String(decoded, StandardCharsets.UTF_8).strip();
        if (!text.startsWith(PEM_BEGIN_MARKER)) {
            return decoded;
        }

        int firstNewline = text.indexOf('\n');
        int footer = text.indexOf(PEM_END_MARKER);
        int labelStart = PEM_BEGIN_MARKER.length();
        int labelEnd = text.indexOf("-----", labelStart);
        if (firstNewline < 0 || footer < 0 || labelEnd < 0 || labelEnd > firstNewline) {
            throw new IllegalStateException(
                    propertyName + " contains malformed PEM key material");
        }

        String label = text.substring(labelStart, labelEnd);
        String body = text.substring(firstNewline + 1, footer).replaceAll("\\s", "");
        byte[] pemDer;
        try {
            pemDer = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    propertyName + " contains invalid PEM base64", exception);
        }

        if (privateKey && "RSA PRIVATE KEY".equals(label)) {
            return wrapPkcs1PrivateKey(pemDer);
        }
        if (!privateKey && "RSA PUBLIC KEY".equals(label)) {
            return wrapPkcs1PublicKey(pemDer);
        }
        if (privateKey && !"PRIVATE KEY".equals(label)) {
            throw new IllegalStateException(
                    propertyName + " PEM must be an unencrypted RSA private key");
        }
        if (!privateKey && !"PUBLIC KEY".equals(label)) {
            throw new IllegalStateException(
                    propertyName + " PEM must be an X.509 RSA public key");
        }

        return pemDer;
    }

    /**
     * Converts an RSA PKCS#1 private key into the PKCS#8 wrapper expected by
     * KeyFactory.
     */
    private static byte[] wrapPkcs1PrivateKey(byte[] pkcs1) {
        byte[] version = new byte[] { 0x02, 0x01, 0x00 };
        byte[] rsaAlgorithm = new byte[] {
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] octetString = derValue((byte) 0x04, pkcs1);
        return derValue((byte) 0x30, concat(version, rsaAlgorithm, octetString));
    }

    /** Converts an RSA PKCS#1 public key into SubjectPublicKeyInfo. */
    private static byte[] wrapPkcs1PublicKey(byte[] pkcs1) {
        byte[] rsaAlgorithm = new byte[] {
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] bitString = derValue((byte) 0x03, concat(new byte[] { 0x00 }, pkcs1));
        return derValue((byte) 0x30, concat(rsaAlgorithm, bitString));
    }

    private static byte[] derValue(byte tag, byte[] value) {
        byte[] length = derLength(value.length);
        return concat(new byte[] { tag }, length, value);
    }

    private static byte[] derLength(int length) {
        if (length < 128) {
            return new byte[] { (byte) length };
        }

        int bytes = 0;
        for (int value = length; value > 0; value >>>= 8) {
            bytes++;
        }

        byte[] result = new byte[bytes + 1];
        result[0] = (byte) (0x80 | bytes);
        for (int index = bytes; index > 0; index--) {
            result[index] = (byte) (length & 0xff);
            length >>>= 8;
        }
        return result;
    }

    private static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }

        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private static String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be set");
        }
        return value.strip();
    }

    private static String readHeaderValue(
            String token, Pattern pattern, String label) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new MalformedJwtException("JWT must contain three segments");
        }

        try {
            String header = new String(
                    Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            Matcher matcher = pattern.matcher(header);
            if (!matcher.find()) {
                throw new MalformedJwtException("JWT header is missing " + label);
            }
            return matcher.group(1);
        } catch (IllegalArgumentException exception) {
            throw new MalformedJwtException(
                    "JWT header is not valid base64url", exception);
        }
    }

    protected static PublicKey parseUserPublicKey(String encoded) {
        return parsePublicKey(encoded, "JWT_PUBLIC_KEY_B64");
    }
}
