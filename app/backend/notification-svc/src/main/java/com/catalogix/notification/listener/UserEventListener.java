package com.catalogix.notification.listener;

import com.catalogix.notification.config.RabbitMQConfig;
import com.catalogix.notification.event.EmailVerificationRequestedEvent;
import com.catalogix.notification.event.PasswordResetRequestedEvent;
import com.catalogix.notification.svc.EmailSvc;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes user.email-verification-requested / user.password-reset-requested
 * events from user-svc, building the actual email subject/body here.
 */
@Component
public class UserEventListener {

    private final EmailSvc emailSvc;

    public UserEventListener(EmailSvc emailSvc) {
        this.emailSvc = emailSvc;
    }

    @RabbitListener(queues = RabbitMQConfig.EMAIL_VERIFICATION_QUEUE)
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        String body = "Hi " + event.userName() + ",\n\n"
                + "Please verify your email address by visiting the link below:\n" + event.verificationLink() + "\n\n"
                + "This link expires in 24 hours. If you didn't create this account, you can ignore this email.";
        emailSvc.send(event.userEmail(), "Verify your Catalogix email", body);
    }

    @RabbitListener(queues = RabbitMQConfig.PASSWORD_RESET_QUEUE)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        String body = "Hi " + event.userName() + ",\n\n"
                + "Reset your password by visiting the link below:\n" + event.resetLink() + "\n\n"
                + "This link expires in 1 hour. If you didn't request this, you can safely ignore this email.";
        emailSvc.send(event.userEmail(), "Reset your Catalogix password", body);
    }
}
