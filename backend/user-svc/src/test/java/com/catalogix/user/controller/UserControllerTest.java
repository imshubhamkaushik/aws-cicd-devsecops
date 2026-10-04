package com.catalogix.user.controller;

import tools.jackson.databind.json.JsonMapper;
import com.catalogix.user.dto.AuthResponse;
import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.ForgotPasswordRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.NotificationPreferencesRequest;
import com.catalogix.user.dto.NotificationPreferencesResponse;
import com.catalogix.user.dto.ResetPasswordRequest;
import com.catalogix.user.dto.SessionResponse;
import com.catalogix.user.dto.TokenPairResponse;
import com.catalogix.user.dto.UpdateProfileRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.exception.AccountLockedException;
import com.catalogix.user.exception.ForbiddenException;
import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.security.JwtAuthFilter;
import com.catalogix.security.RateLimiterFilter;
import com.catalogix.user.security.RefreshTokenService;
import com.catalogix.user.svc.UserSvc;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

// Auth is exercised via requestAttr(...) (simulating what JwtAuthFilter would set) rather
// than via a real token, so JwtAuthFilter/RateLimiterFilter are excluded from this slice —
// they'd otherwise need a real JwtService bean (JWT_SECRET etc.) just to construct.
@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthFilter.class, RateLimiterFilter.class}))
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private JsonMapper mapper;

    @MockitoBean
    private UserSvc svc;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @Autowired
    private MockMvc mvc;

    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 days, matches prod default

    private static final String REFRESH_COOKIE = "catalogix_refresh_token";

    private AuthResponse sampleAuthResponse() {
        UserResponse profile = new UserResponse(1L, "John", "john@example.com", "USER");
        return new AuthResponse("fake.access.token", 900000L, "fake-refresh-token", profile);
    }

    // POST /users/register tests
    @Test
    @SuppressWarnings("null")
    void registerReturnsCreated() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("Password1");

        when(svc.register(any(), any())).thenReturn(sampleAuthResponse());
        when(refreshTokenService.getExpirationMs()).thenReturn(REFRESH_EXPIRATION_MS);

        mvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("fake.access.token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.id").value(1L))
                .andExpect(jsonPath("$.user.name").value("John"))
                .andExpect(jsonPath("$.user.email").value("john@example.com"))
                .andExpect(header().string("Set-Cookie", containsString(REFRESH_COOKIE + "=fake-refresh-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/users")));
    }

    @Test
    @SuppressWarnings("null")
    void registerDuplicateEmailReturns409() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("John");
        req.setEmail("john@example.com");
        req.setPassword("Password1");

        when(svc.register(any(), any())).thenThrow(new IllegalArgumentException("Email already registered"));

        mvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    // POST /users/login tests
    @Test
    @SuppressWarnings("null")
    void loginReturnsOkWithTokens() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("john@example.com");
        req.setPassword("Password1");

        when(svc.login(any(), any())).thenReturn(sampleAuthResponse());
        when(refreshTokenService.getExpirationMs()).thenReturn(REFRESH_EXPIRATION_MS);

        mvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake.access.token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value("john@example.com"))
                .andExpect(header().string("Set-Cookie", containsString(REFRESH_COOKIE + "=fake-refresh-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    @SuppressWarnings("null")
    void loginFailureReturns401() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("x@x.com");
        req.setPassword("wrongpass");

        when(svc.login(any(), any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @SuppressWarnings("null")
    void loginLockedReturns429WithRetryAfterHeader() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("locked@x.com");
        req.setPassword("whatever");

        when(svc.login(any(), any())).thenThrow(new AccountLockedException(Duration.ofSeconds(120)));

        mvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
    }

    // POST /users/refresh tests
    @Test
    @SuppressWarnings("null")
    void refreshReturnsNewTokenPair() throws Exception {
        when(svc.refresh("old-token"))
                .thenReturn(new TokenPairResponse("new.access.token", 900000L, "new-refresh-token"));
        when(refreshTokenService.getExpirationMs()).thenReturn(REFRESH_EXPIRATION_MS);

        mvc.perform(post("/users/refresh")
                .cookie(new Cookie(REFRESH_COOKIE, "old-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString(REFRESH_COOKIE + "=new-refresh-token")));
    }

    @Test
    @SuppressWarnings("null")
    void refreshWithInvalidTokenReturns401() throws Exception {
        when(svc.refresh("bogus")).thenThrow(new UnauthorizedException("Invalid refresh token"));

        mvc.perform(post("/users/refresh")
                .cookie(new Cookie(REFRESH_COOKIE, "bogus")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithNoCookieReturns401() throws Exception {
        mvc.perform(post("/users/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // POST /users/logout, /users/logout-all
    @Test
    @SuppressWarnings("null")
    void logoutReturnsNoContent() throws Exception {
        mvc.perform(post("/users/logout")
                .cookie(new Cookie(REFRESH_COOKIE, "some-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString(REFRESH_COOKIE + "=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(svc).logout("some-token");
    }

    @Test
    void logoutWithNoCookieReturnsNoContentWithoutCallingSvc() throws Exception {
        // Missing cookie = "already logged out" — never an error (see
        // UserController's Javadoc). svc.logout must not be invoked with a
        // null/blank token.
        mvc.perform(post("/users/logout"))
                .andExpect(status().isNoContent());

        verify(svc, never()).logout(any());
    }

    @Test
    void logoutAllReturnsNoContent() throws Exception {
        mvc.perform(post("/users/logout-all").requestAttr("userId", 1L))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(svc).logoutEverywhere(1L);
    }

    // GET /users tests (admin-only directory)

    @Test
    @SuppressWarnings("null")
    void getAllReturnsListOfUsersForAdmin() throws Exception {
        when(svc.listAll()).thenReturn(List.of(
                new UserResponse(1L, "Alice", "alice@example.com", "ADMIN"),
                new UserResponse(2L, "Bob",   "bob@example.com", "USER")
        ));

        mvc.perform(get("/users")
                .requestAttr("userId", 1L)
                .requestAttr("userRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Bob"));
    }

    @Test
    void getAllRejectsNonAdmin() throws Exception {
        mvc.perform(get("/users")
                .requestAttr("userId", 2L)
                .requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    // DELETE /users/{id} tests

    @Test
    void deleteReturnsNoContentForSelf() throws Exception {
        when(svc.deleteById(1L, 1L, "USER")).thenReturn(true);

        mvc.perform(delete("/users/1")
                .requestAttr("userId", 1L)
                .requestAttr("userRole", "USER"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturnsNotFoundWhenUserMissing() throws Exception {
        when(svc.deleteById(99L, 1L, "ADMIN")).thenReturn(false);

        mvc.perform(delete("/users/99")
                .requestAttr("userId", 1L)
                .requestAttr("userRole", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturnsForbiddenForOtherUsersAccount() throws Exception {
        when(svc.deleteById(anyLong(), anyLong(), anyString()))
                .thenThrow(new ForbiddenException("You may only delete your own account"));

        mvc.perform(delete("/users/2")
                .requestAttr("userId", 1L)
                .requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    // GET /users/verify-email
    @Test
    void verifyEmailReturnsNoContentOnSuccess() throws Exception {
        mvc.perform(get("/users/verify-email").param("token", "raw-token"))
                .andExpect(status().isNoContent());
        verify(svc).verifyEmail("raw-token");
    }

    @Test
    void verifyEmailReturnsUnauthorizedForBadToken() throws Exception {
        doThrow(new UnauthorizedException("Invalid or expired verification link"))
                .when(svc).verifyEmail("bogus");

        mvc.perform(get("/users/verify-email").param("token", "bogus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resendVerificationReturnsAccepted() throws Exception {
        mvc.perform(post("/users/resend-verification").requestAttr("userId", 1L))
                .andExpect(status().isAccepted());
        verify(svc).resendVerificationEmail(1L);
    }

    // POST /users/forgot-password
    @Test
    @SuppressWarnings("null")
    void forgotPasswordAlwaysReturnsAcceptedRegardlessOfWhetherEmailExists() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("maybe@example.com");

        mvc.perform(post("/users/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());

        verify(svc).forgotPassword("maybe@example.com");
    }

    // POST /users/reset-password
    @Test
    @SuppressWarnings("null")
    void resetPasswordReturnsNoContentOnSuccess() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("raw-token");
        req.setNewPassword("NewPassword1");

        mvc.perform(post("/users/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(svc).resetPassword("raw-token", "NewPassword1");
    }

    @Test
    @SuppressWarnings("null")
    void resetPasswordReturnsUnauthorizedForExpiredToken() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("expired-token");
        req.setNewPassword("NewPassword1");

        doThrow(new UnauthorizedException("Invalid or expired reset link"))
                .when(svc).resetPassword("expired-token", "NewPassword1");

        mvc.perform(post("/users/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // PATCH /users/me
    @Test
    @SuppressWarnings("null")
    void updateProfileReturnsUpdatedUser() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("New Name");

        when(svc.updateProfile(eq(1L), any(UpdateProfileRequest.class), any()))
                .thenReturn(new UserResponse(1L, "New Name", "x@x.com", "USER", true));

        mvc.perform(patch("/users/me")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    @SuppressWarnings("null")
    void updateProfileReturnsUnauthorizedWhenCurrentPasswordMissing() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail("new@x.com");

        when(svc.updateProfile(eq(1L), any(UpdateProfileRequest.class), any()))
                .thenThrow(new UnauthorizedException("currentPassword is required and must be correct"));

        mvc.perform(patch("/users/me")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // GET /users/me/sessions
    @Test
    void listSessionsReturnsSessionsForCurrentUser() throws Exception {
        SessionResponse session = new SessionResponse(
                10L, "Chrome on Windows", java.time.Instant.now(), java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(3600), true);
        when(svc.listSessions(eq(1L), any())).thenReturn(List.of(session));

        mvc.perform(get("/users/me/sessions")
                .requestAttr("userId", 1L)
                .cookie(new Cookie(REFRESH_COOKIE, "raw-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void listSessionsWorksWithoutARefreshCookie() throws Exception {
        when(svc.listSessions(eq(1L), any())).thenReturn(List.of());

        mvc.perform(get("/users/me/sessions").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // DELETE /users/me/sessions/{id} // revokes a session
    @Test
    void revokeSessionReturnsNoContent() throws Exception {
        mvc.perform(delete("/users/me/sessions/10").requestAttr("userId", 1L))
                .andExpect(status().isNoContent());

        verify(svc).revokeSession(10L, 1L);
    }

    @Test
    void revokeSessionReturnsForbiddenForAnotherUsersSession() throws Exception {
        doThrow(new ForbiddenException("You may only revoke your own sessions"))
                .when(svc).revokeSession(10L, 1L);

        mvc.perform(delete("/users/me/sessions/10").requestAttr("userId", 1L))
                .andExpect(status().isForbidden());
    }

    // PATCH /users/me/notification-preferences
    @Test
    @SuppressWarnings("null")
    void updateNotificationPreferencesReturnsUpdatedUser() throws Exception {
        NotificationPreferencesRequest req = new NotificationPreferencesRequest();
        req.setOrderEmailsEnabled(false);

        UserResponse updated = new UserResponse();
        updated.setId(1L);
        updated.setName("John");
        updated.setEmail("john@example.com");
        updated.setRole("USER");
        updated.setVerified(true);
        updated.setCreatedAt(java.time.Instant.now());
        updated.setOrderEmailsEnabled(false);
        when(svc.updateNotificationPreferences(eq(1L), any(NotificationPreferencesRequest.class)))
                .thenReturn(updated);

        mvc.perform(patch("/users/me/notification-preferences")
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderEmailsEnabled").value(false));
                    }

    // GET /users/{id}/notification-preferences — internal, SYSTEM-only
    @Test
    void getNotificationPreferencesReturnsForASystemCaller() throws Exception {
        when(svc.getNotificationPreferences(5L))
                .thenReturn(new NotificationPreferencesResponse(false));

        mvc.perform(get("/users/5/notification-preferences").requestAttr("userRole", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderEmailsEnabled").value(false));
                    }

    @Test
    void getNotificationPreferencesRejectsARegularUserToken() throws Exception {
        mvc.perform(get("/users/5/notification-preferences").requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());
    }

    // POST /users/me/become-seller — records a PENDING request; the role does not change
    @Test
    @SuppressWarnings("null")
    void becomeSellerReturnsTheProfileWithAPendingRequest() throws Exception {
        UserResponse pending = new UserResponse();
        pending.setId(1L);
        pending.setName("John");
        pending.setEmail("john@example.com");
        pending.setRole("USER");
        pending.setVerified(true);
        pending.setCreatedAt(java.time.Instant.now());
        pending.setOrderEmailsEnabled(true);
        pending.setRequestedRole("SELLER");
        when(svc.becomeSeller(1L)).thenReturn(pending);

        mvc.perform(post("/users/me/become-seller").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.requestedRole").value("SELLER"));
    }

    // PUT /users/{id}/role — admin only
    @Test
    @SuppressWarnings("null")
    void assignRoleAsAdminReturnsTheUpdatedUser() throws Exception {
        when(svc.assignRole(2L, "SELLER", 1L))
                .thenReturn(new UserResponse(2L, "Bob", "bob@example.com", "SELLER", true));

        mvc.perform(put("/users/2/role")
                .requestAttr("userId", 1L)
                .requestAttr("userRole", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"SELLER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    @SuppressWarnings("null")
    void assignRoleRejectsANonAdmin() throws Exception {
        mvc.perform(put("/users/2/role")
                .requestAttr("userId", 5L)
                .requestAttr("userRole", "SELLER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        verify(svc, never()).assignRole(anyLong(), anyString(), anyLong());
    }

    @Test
    @SuppressWarnings("null")
    void assignRoleRejectsAnUnknownRoleValueWith400() throws Exception {
        mvc.perform(put("/users/2/role")
                .requestAttr("userId", 1L)
                .requestAttr("userRole", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
    }

    // DELETE /users/{id}/role-request — admin only
    @Test
    void rejectRoleRequestAsAdminSucceeds() throws Exception {
        when(svc.rejectRoleRequest(2L))
                .thenReturn(new UserResponse(2L, "Bob", "bob@example.com", "USER", true));

        mvc.perform(delete("/users/2/role-request").requestAttr("userRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void rejectRoleRequestRejectsANonAdmin() throws Exception {
        mvc.perform(delete("/users/2/role-request").requestAttr("userRole", "USER"))
                .andExpect(status().isForbidden());

        verify(svc, never()).rejectRoleRequest(anyLong());
    }
}
