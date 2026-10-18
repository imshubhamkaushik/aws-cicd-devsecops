package com.catalogix.user.security;

import com.catalogix.user.config.RabbitMQConfig;
import com.catalogix.user.event.EmailVerificationRequestedEvent;
import com.catalogix.user.event.PasswordResetRequestedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private UserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new UserEventPublisher(rabbitTemplate);
    }

    @Test
    void onEmailVerificationRequestedPublishesWithTheRightRoutingKey() {
        EmailVerificationRequestedEvent event =
                new EmailVerificationRequestedEvent("x@x.com", "Name", "http://localhost:8080/verify-email?token=abc");

        publisher.onEmailVerificationRequested(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE, "user.email-verification-requested", event);
    }

    @Test
    void onPasswordResetRequestedPublishesWithTheRightRoutingKey() {
        PasswordResetRequestedEvent event =
                new PasswordResetRequestedEvent("x@x.com", "Name", "http://localhost:8080/reset-password?token=abc");

        publisher.onPasswordResetRequested(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EVENTS_EXCHANGE, "user.password-reset-requested", event);
    }

    @Test
    void aBrokerFailureDuringPublishIsSwallowedNotThrown() {
        doThrow(new RuntimeException("broker unreachable"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        EmailVerificationRequestedEvent event =
                new EmailVerificationRequestedEvent(
                        "x@x.com",
                        "Name",
                        "link");

        assertDoesNotThrow(() ->
                publisher.onEmailVerificationRequested(event));
    }
}
