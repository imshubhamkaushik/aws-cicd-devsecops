package com.catalogix.checkout.controller;

import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Auth is exercised via requestAttr(...) (simulating what JwtAuthFilter would set) rather
// than via a real token, so JwtAuthFilter/RateLimiterFilter are excluded from this slice —
// they'd otherwise need a real JwtService bean (JWT_SECRET etc.) just to construct.
// AdminController talks straight to CompensationOutboxRepository (there's no
// AdminSvc), so that's what gets mocked here rather than a service class.
@WebMvcTest(
        controllers = AdminController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
class AdminControllerTest {

    @MockitoBean
    private CompensationOutboxRepository outboxRepo;

    @Autowired
    private MockMvc mvc;

    @Test
    void listReturnsPendingAndDeadLetterEntriesForAdmin() throws Exception {
        CompensationOutbox entry = CompensationOutbox.releaseStock(1L, 2, "cancel-order-5");
        entry.setId(9L);
        when(outboxRepo.findByStatusInOrderByCreatedAtDesc(List.of(OutboxStatus.PENDING, OutboxStatus.DEAD_LETTER)))
                .thenReturn(List.of(entry));

        mvc.perform(get("/admin/outbox").requestAttr("userRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("RELEASE_STOCK"))
                .andExpect(jsonPath("$[0].productId").value(1L));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        mvc.perform(get("/admin/outbox").requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void retryResetsAnEntryBackToPending() throws Exception {
        CompensationOutbox entry = CompensationOutbox.releaseStock(1L, 2, "payment declined");
        entry.setId(9L);
        entry.setStatus(OutboxStatus.DEAD_LETTER);
        entry.setAttempts(5);
        entry.setLastError("inventory-svc unreachable");
        when(outboxRepo.findById(9L)).thenReturn(Optional.of(entry));
        when(outboxRepo.save(any(CompensationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/admin/outbox/9/retry").requestAttr("userRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.lastError").doesNotExist());
    }

    @Test
    void retryReturnsBadRequestWhenEntryDoesNotExist() throws Exception {
        when(outboxRepo.findById(99L)).thenReturn(Optional.empty());

        // AdminController throws IllegalArgumentException here, which this
        // service maps to 400 Bad Request — see GlobalExceptionHandler.
        mvc.perform(post("/admin/outbox/99/retry").requestAttr("userRole", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retryRejectsNonAdmin() throws Exception {
        mvc.perform(post("/admin/outbox/9/retry").requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }
}
