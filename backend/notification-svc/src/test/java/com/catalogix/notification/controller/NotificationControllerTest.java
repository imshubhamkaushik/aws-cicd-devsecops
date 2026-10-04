package com.catalogix.notification.controller;

import com.catalogix.notification.model.Notification;
import com.catalogix.notification.model.NotificationStatus;
import com.catalogix.notification.repository.NotificationRepository;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Auth is exercised via requestAttr(...) (simulating what JwtAuthFilter would set) rather
// than via a real token, so JwtAuthFilter/RateLimiterFilter are excluded from this slice —
// they'd otherwise need a real JwtService bean (JWT_SECRET etc.) just to construct.
@WebMvcTest(
        controllers = NotificationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
class NotificationControllerTest {

    @MockitoBean
    private NotificationRepository repo;

    @Autowired
    private MockMvc mvc;

    private Notification sampleNotification() {
        Notification n = new Notification("alice@example.com", "Welcome", "Hello Alice!");
        n.setId(1L);
        n.setStatus(NotificationStatus.SENT);
        return n;
    }

    @Test
    void listReturnsPagedLogForAdmin() throws Exception {
        when(repo.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(sampleNotification()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/notifications").requestAttr("userRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].recipient").value("alice@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        mvc.perform(get("/notifications").requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }
}
