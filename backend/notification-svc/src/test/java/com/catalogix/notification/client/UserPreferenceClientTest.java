package com.catalogix.notification.client;

import com.catalogix.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UserPreferenceClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private JwtService jwtService;

    private UserPreferenceClient client;

    private static final String USER_SVC_URL = "http://user-svc:11005";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new UserPreferenceClient(restTemplate, USER_SVC_URL, jwtService);
        when(jwtService.generateSystemToken()).thenReturn("system.jwt.token");
    }

    @Test
    void returnsTheRemotePreferenceWhenTheCallSucceeds() {
        var payload = new UserPreferenceClient.PreferencesPayload(false);
        when(restTemplate.exchange(
                eq(USER_SVC_URL + "/users/1/notification-preferences"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(UserPreferenceClient.PreferencesPayload.class)))
                .thenReturn(ResponseEntity.ok(payload));

        assertFalse(client.isOrderEmailsEnabled(1L));
    }

    @Test
    void failsOpenWhenUserSvcIsUnreachable() {
        when(restTemplate.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(UserPreferenceClient.PreferencesPayload.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertTrue(client.isOrderEmailsEnabled(1L));
    }

    @Test
    void failsOpenWhenUserIdIsNull() {
        assertTrue(client.isOrderEmailsEnabled(null));
    }
}
