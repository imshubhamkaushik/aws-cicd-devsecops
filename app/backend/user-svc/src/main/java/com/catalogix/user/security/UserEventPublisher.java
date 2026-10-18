package com.catalogix.user.security;

import com.catalogix.user.config.RabbitMQConfig;
import com.catalogix.user.event.EmailVerificationRequestedEvent;
import com.catalogix.user.event.PasswordResetRequestedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays UserSvc's in-JVM events to RabbitMQ for notification-svc. Runs after the DB commit
 * and asynchronously (@TransactionalEventListener(AFTER_COMMIT) + @Async), so a slow broker
 * never blocks a request; delivery is best-effort, not guaranteed.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        publish("user.email-verification-requested", event, event.userEmail());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        publish("user.password-reset-requested", event, event.userEmail());
    }

    private void publish(String routingKey, Object event, String userEmail) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EVENTS_EXCHANGE, routingKey, event);
        } catch (RuntimeException e) {
            log.warn("Failed to publish {} for {}: {}", routingKey, userEmail, e.getMessage());
        }
    }
}
