package com.catalogix.notification.client;

import com.catalogix.security.JwtService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Checks a user's notification preferences (see user-svc's
 * NotificationPreferencesResponse) before OrderEventListener decides whether
 * to actually send an order-status email.
 *
 * FAILS OPEN on purpose: if user-svc is slow, down, or returns something
 * unexpected, this returns "send it" rather than "don't send it". A missed
 * order-confirmation email because a preference check failed is a worse
 * outcome than occasionally sending one to someone who'd opted out — the
 * former looks like the order itself silently failed; the latter is a minor
 * annoyance. Same reasoning as user-svc's own getNotificationPreferences
 * endpoint defaulting to true/true for an unknown user.
 *
 * Uses a plain RestTemplate + try/catch rather than a @CircuitBreaker
 * (unlike checkout-svc's InventoryClient) — resilience4j isn't a dependency
 * of this service, and adding one just for this one best-effort, already-
 * fail-open call wasn't worth the extra surface area.
 */
@Component
public class UserPreferenceClient {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceClient.class);

    private final RestTemplate restTemplate;
    private final String userSvcUrl;
    private final JwtService jwtService;

    public UserPreferenceClient(RestTemplate restTemplate, @Value("${USER_SVC_URL}") String userSvcUrl,
                                 JwtService jwtService) {
        this.restTemplate = restTemplate;
        this.userSvcUrl = userSvcUrl;
        this.jwtService = jwtService;
    }

    public boolean isOrderEmailsEnabled(Long userId) {
        if (userId == null) {
            // Events published before userId existed on the payload (or a
            // future bug that drops it) shouldn't silently swallow the
            // email — same fail-open reasoning as everything else here.
            return true;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateSystemToken());

            var response = restTemplate.exchange(
                    userSvcUrl + "/users/" + userId + "/notification-preferences",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    PreferencesPayload.class);

            PreferencesPayload body = response.getBody();
            return body == null || body.orderEmailsEnabled();
        } catch (RestClientException e) {
            log.warn("Failed to look up notification preferences for user {} — sending anyway (fail-open): {}",
                    userId, e.getMessage());
            return true;
        }
    }

    // Minimal shape matching user-svc's NotificationPreferencesResponse —
    // deliberately not sharing that DTO class across services, same reasoning
    // as OrderConfirmedEvent's Javadoc on why events/DTOs are duplicated per
    // service rather than shared. Package-private (not private) so the test
    // in this same package can construct one directly.
    record PreferencesPayload(boolean orderEmailsEnabled) {
    }
}
