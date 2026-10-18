package com.catalogix.checkout.controller;

import com.catalogix.checkout.dto.OutboxEntryResponse;
import com.catalogix.checkout.exception.ForbiddenException;
import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Operational visibility into the compensation outbox (see
 * CompensationOutboxProcessor) — admin-only. Not a general-purpose admin
 * API; just enough to see what's stuck and give it another push.
 */
@RestController
@RequestMapping("/admin/outbox")
public class AdminController {

    private final CompensationOutboxRepository outboxRepo;

    public AdminController(CompensationOutboxRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
    }

    @GetMapping
    public List<OutboxEntryResponse> list(@RequestAttribute("userRole") String role) {
        requireAdmin(role);
        return outboxRepo.findByStatusInOrderByCreatedAtDesc(List.of(OutboxStatus.PENDING, OutboxStatus.DEAD_LETTER))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<OutboxEntryResponse> retry(
            @PathVariable Long id,
            @RequestAttribute("userRole") String role
    ) {
        requireAdmin(role);
        CompensationOutbox entry = outboxRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Outbox entry not found: " + id));
        entry.setStatus(OutboxStatus.PENDING);
        entry.setAttempts(0);
        entry.setLastError(null);
        entry.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(toResponse(outboxRepo.save(entry)));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Admin access required");
        }
    }

    private OutboxEntryResponse toResponse(CompensationOutbox e) {
        OutboxEntryResponse response = new OutboxEntryResponse();
        response.setId(e.getId());
        response.setType(e.getType());
        response.setProductId(e.getProductId());
        response.setDelta(e.getDelta());
        response.setReason(e.getReason());
        response.setStatus(e.getStatus());
        response.setAttempts(e.getAttempts());
        response.setLastError(e.getLastError());
        response.setCreatedAt(e.getCreatedAt());
        response.setUpdatedAt(e.getUpdatedAt());
        return response;
    }
}
