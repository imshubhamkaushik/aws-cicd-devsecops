package com.catalogix.notification.listener;

import com.catalogix.notification.event.EmailVerificationRequestedEvent;
import com.catalogix.notification.event.PasswordResetRequestedEvent;
import com.catalogix.notification.svc.EmailSvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserEventListenerTest {

    @Mock private EmailSvc emailSvc;

    private UserEventListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new UserEventListener(emailSvc);
    }

    @Test
    @SuppressWarnings("null")
    void onEmailVerificationRequestedBuildsAnEmailWithTheLink() {
        EmailVerificationRequestedEvent event = new EmailVerificationRequestedEvent(
                "alice@example.com", "Alice", "http://localhost:8080/verify-email?token=abc", null);

        listener.onEmailVerificationRequested(event);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSvc).send(eq("alice@example.com"), eq("Verify your Catalogix email"), bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("Alice"));
        assertTrue(bodyCaptor.getValue().contains("token=abc"));
    }

    @Test
    @SuppressWarnings("null")
    void onPasswordResetRequestedBuildsAnEmailWithTheLink() {
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent(
                "alice@example.com", "Alice", "http://localhost:8080/reset-password?token=xyz", null);

        listener.onPasswordResetRequested(event);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSvc).send(eq("alice@example.com"), eq("Reset your Catalogix password"), bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("token=xyz"));
    }
}
