package com.catalogix.notification.controller;

import com.catalogix.notification.dto.NotificationLogResponse;
import com.catalogix.notification.dto.PagedResponse;
import com.catalogix.notification.exception.ForbiddenException;
import com.catalogix.notification.model.Notification;
import com.catalogix.notification.repository.NotificationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only, read-only view of what has been sent. Sending happens in the RabbitMQ
 * consumers (see the `listener` package); there is no inbound "send an email" endpoint.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public PagedResponse<NotificationLogResponse> list(
            @RequestAttribute("userRole") String role,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Only admins may view the notification log");
        }
        Page<Notification> page = repo.findAllByOrderByCreatedAtDesc(pageable);
        return PagedResponse.from(page, page.getContent().stream().map(this::toResponse).toList());
    }

    private NotificationLogResponse toResponse(Notification n) {
        return new NotificationLogResponse(n.getId(), n.getRecipient(), n.getSubject(), n.getStatus(), n.getError(), n.getCreatedAt());
    }
}
