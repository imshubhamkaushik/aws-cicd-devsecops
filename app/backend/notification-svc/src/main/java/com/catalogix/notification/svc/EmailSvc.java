package com.catalogix.notification.svc;

import com.catalogix.notification.dto.NotificationResponse;
import com.catalogix.notification.model.Notification;
import com.catalogix.notification.model.NotificationStatus;
import com.catalogix.notification.repository.NotificationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends a plain-text email and records the attempt (success or failure) in
 * notification_log either way — an audit trail of every attempt, including
 * retries (see below).
 *
 * On failure, this RETHROWS after logging — unlike the old HTTP-endpoint
 * version, which swallowed the failure and returned a FAILED status in the
 * response body. Now that the only caller is a @RabbitListener (see the
 * `listener` package), rethrowing is what lets Spring AMQP's retry policy
 * (spring.rabbitmq.listener.simple.retry.*) actually kick in — and, once
 * retries are exhausted, dead-letters the message into catalogix.events.dlq
 * (see RabbitMQConfig) instead of it being silently lost.
 */
@Service
public class EmailSvc {

    private final JavaMailSender mailSender;
    private final NotificationRepository repo;
    private final String fromAddress;

    public EmailSvc(
            JavaMailSender mailSender,
            NotificationRepository repo,
            @Value("${MAIL_FROM:noreply@catalogix.local}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.repo = repo;
        this.fromAddress = fromAddress;
    }

    // noRollbackFor is the actual fix here: MailException is the expected
    // failure path (see class Javadoc — rethrowing is what drives the AMQP
    // retry), not a reason to discard the audit row this method just wrote.
    // Spring's default @Transactional rolls back on ANY unchecked exception
    // before the method returns — without this annotation, the repo.save()
    // in the catch block below would be silently undone every single time,
    // making the "audit trail of every attempt" claim in the class Javadoc
    // false for every FAILED attempt. (The unit test for this class predates
    // the fix and can't catch the bug either way — it builds EmailSvc
    // directly with `new EmailSvc(...)`, bypassing Spring's transactional
    // proxy entirely, so it never observes real commit/rollback behavior.)
    @Transactional(noRollbackFor = MailException.class)
    public NotificationResponse send(String to, String subject, String body) {
        Notification entry = new Notification(to, subject, body);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            entry.setStatus(NotificationStatus.SENT);
        } catch (MailException e) {
            entry.setStatus(NotificationStatus.FAILED);
            entry.setError(truncate(e.getMessage()));
            repo.save(entry);

            throw new MailSendException(
                    "Failed to send email to " + to,
                    e);
        }

        Notification saved = repo.save(entry);
        return new NotificationResponse(saved.getId(), saved.getStatus(), saved.getError());
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
