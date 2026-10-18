package com.catalogix.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regression tests for asymmetric user-token verification and internal service-token checks. */
class JwtServiceRotationTest {

    @Test
    void acceptsUserAccessTokenSignedByUserService() {
        JwtService verifier = JwtTestKeys.service("catalog-svc");
        Claims claims = verifier.parseClaims(
                JwtTestKeys.userAccessToken(42L, "USER", "catalogix-api"), "catalog-svc");
        assertThat(claims.getIssuer()).isEqualTo("user-svc");
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    void acceptsBase64WrappedPemKeyMaterialUsedByTerraform() {
        JwtService checkout = JwtTestKeys.serviceFromPem("checkout-svc");
        String token = checkout.generateSystemToken("inventory-svc");
        Claims claims = JwtTestKeys.serviceFromPem("inventory-svc").parseClaims(token, "inventory-svc");
        assertThat(claims.getIssuer()).isEqualTo("checkout-svc");
    }

    @Test
    void refusesToMintTokensForAnInvalidTargetService() {
        JwtService checkout = JwtTestKeys.service("checkout-svc");
        assertThatThrownBy(() -> checkout.generateSystemToken("Not A Service!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsServiceTokenOnlyForItsIntendedAudience() {
        JwtService checkout = JwtTestKeys.service("checkout-svc");
        JwtService inventory = JwtTestKeys.service("inventory-svc");
        String token = checkout.generateSystemToken("inventory-svc");

        assertThat(inventory.parseClaims(token, "inventory-svc").getIssuer()).isEqualTo("checkout-svc");
        assertThatThrownBy(() -> JwtTestKeys.service("payment-svc").parseClaims(token, "payment-svc"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokensNotSignedByTheInternalKeyEvenWhenTheyClaimSystemRole() {
        JwtService inventory = JwtTestKeys.service("inventory-svc");
        String rogueToken = JwtTestKeys.rogueServiceToken("rogue-svc", "inventory-svc");
        assertThatThrownBy(() -> inventory.parseClaims(rogueToken, "inventory-svc"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUserTokenWithUnsupportedRoleOrWrongAudience() {
        JwtService verifier = JwtTestKeys.service("catalog-svc");
        assertThatThrownBy(() -> verifier.parseClaims(
                JwtTestKeys.userAccessToken(12L, "SYSTEM", "catalogix-api"), "catalog-svc"))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> verifier.parseClaims(
                JwtTestKeys.userAccessToken(12L, "USER", "other-api"), "catalog-svc"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsNonPositiveUserSubjects() {
        JwtService verifier = JwtTestKeys.service("catalog-svc");
        assertThatThrownBy(() -> verifier.parseClaims(
                JwtTestKeys.userAccessToken(0L, "USER", "catalogix-api"), "catalog-svc"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aServiceCannotUseItsTokenToImpersonateAnotherServiceByChangingTheAudience() {
        JwtService checkout = JwtTestKeys.service("checkout-svc");
        JwtService inventory = JwtTestKeys.service("inventory-svc");
        String token = checkout.generateSystemToken("payment-svc");
        assertThatThrownBy(() -> inventory.parseClaims(token, "inventory-svc"))
                .isInstanceOf(JwtException.class);
    }
}
