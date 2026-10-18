package com.catalogix.notification.svc;

import com.catalogix.notification.dto.NotificationResponse;
import com.catalogix.notification.model.Notification;
import com.catalogix.notification.model.NotificationStatus;
import com.catalogix.notification.repository.NotificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailSvcTest {

    @Mock private JavaMailSender mailSender;
    @Mock private NotificationRepository repo;

    private EmailSvc svc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        svc = new EmailSvc(mailSender, repo, "noreply@catalogix.local");
        when(repo.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });
    }

    @Test
    void sendRecordsSentStatusOnSuccess() {
        NotificationResponse resp = svc.send("alice@example.com", "Welcome", "Hi Alice!");

        assertEquals(NotificationStatus.SENT, resp.getStatus());
        assertNull(resp.getError());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @SuppressWarnings("null")
    void sendUsesTheConfiguredFromAddress() {
        svc.send("alice@example.com", "Welcome", "Hi Alice!");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("noreply@catalogix.local", captor.getValue().getFrom());
        assertArrayEquals(new String[]{"alice@example.com"}, captor.getValue().getTo());
    }

    @Test
    void sendRecordsFailedStatusAndRethrowsOnMailException() {
        doThrow(new MailSendException("SMTP connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        // Rethrows deliberately — the caller is a @RabbitListener, and only a thrown
        // exception lets Spring AMQP's retry/dead-letter policy actually kick in.
        assertThrows(MailSendException.class, () -> svc.send("alice@example.com", "Welcome", "Hi Alice!"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertEquals(NotificationStatus.FAILED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getError());
    }
}
