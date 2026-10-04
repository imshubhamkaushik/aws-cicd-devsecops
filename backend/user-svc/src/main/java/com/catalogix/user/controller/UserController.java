package com.catalogix.user.controller;

import com.catalogix.user.dto.AuthResponse;
import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.ForgotPasswordRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.NotificationPreferencesRequest;
import com.catalogix.user.dto.NotificationPreferencesResponse;
import com.catalogix.user.dto.ResetPasswordRequest;
import com.catalogix.user.dto.RoleAssignmentRequest;
import com.catalogix.user.dto.SessionResponse;
import com.catalogix.user.dto.TokenPairResponse;
import com.catalogix.user.dto.UpdateProfileRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.exception.ForbiddenException;
import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.user.security.RefreshTokenService;
import com.catalogix.user.svc.UserSvc;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    // Scoped to /users so the cookie is only sent to the endpoints that need it
    // (/users/refresh, /users/logout, /users/logout-all).
    private static final String REFRESH_COOKIE_NAME = "catalogix_refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/users";

    private final UserSvc svc;
    private final RefreshTokenService refreshTokenService;

    // Defaults to true (deployments sit behind TLS). Set REFRESH_COOKIE_SECURE=false only for
    // local HTTP: browsers discard a Secure cookie set over plain HTTP, so login would appear
    // to work while refresh never succeeds.
    @Value("${REFRESH_COOKIE_SECURE:true}")
    private boolean cookieSecure;

    public UserController(UserSvc svc, RefreshTokenService refreshTokenService) {
        this.svc = svc;
        this.refreshTokenService = refreshTokenService;
    }

    // Registers a new user, sends a verification email and returns tokens so the frontend can
    // log the user straight in. The refresh token is never in the JSON body (see AuthResponse).
    // User-Agent is captured only for the session list.
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody CreateUserRequest req,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletResponse response
    ) {
        AuthResponse created = svc.register(req, userAgent);
        setRefreshCookie(response, created.getRefreshToken());
        return ResponseEntity.status(201).body(created);
    }

    // Validates credentials and returns tokens plus the profile.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletResponse response
    ) {
        AuthResponse result = svc.login(req, userAgent);
        setRefreshCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(result);
    }

    // Exchanges the refresh token from the httpOnly cookie (never the request body) for a new
    // access token, rotating the cookie to the new refresh token. The old token stops working
    // as soon as this succeeds.
    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token cookie");
        }
        TokenPairResponse result = svc.refresh(refreshToken);
        setRefreshCookie(response, result.getRefreshToken());
        return ResponseEntity.ok(result);
    }

    // Logs out this session: revokes the refresh token from the cookie and clears the cookie.
    // The access token stays valid until it expires (short-lived by design). A missing cookie
    // counts as already logged out, so logout never fails loudly.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            svc.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    // Logs out every session (revokes all refresh tokens) and clears this browser's cookie too.
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutEverywhere(@RequestAttribute("userId") Long userId, HttpServletResponse response) {
        svc.logoutEverywhere(userId);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                // Strict, not Lax: the gateway is the SPA's own origin, so no legitimate cross-site
                // navigation needs this cookie.
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshTokenService.getExpirationMs()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // Target of the link in the verification email; a plain link, hence GET.
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        svc.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    // For when the original verification email was lost or expired. Requires auth because it is
    // tied to the caller's own account, unlike forgot-password.
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestAttribute("userId") Long userId) {
        svc.resendVerificationEmail(userId);
        return ResponseEntity.accepted().build();
    }

    // Always returns 202 regardless of whether the email exists, to avoid leaking registered emails.
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        svc.forgotPassword(req.getEmail());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        svc.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    // Current authenticated user's own profile.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(svc.findById(userId));
    }

    // Updates the current user's name/email/password. A password change signs out every other
    // session; this request's refresh cookie identifies the one to keep.
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody UpdateProfileRequest req,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        return ResponseEntity.ok(svc.updateProfile(userId, req, refreshToken));
    }

    // Admin directory of all users.
    @GetMapping
    public List<UserResponse> getAll(@RequestAttribute("userRole") String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Only admins may list all users");
        }
        return svc.listAll();
    }

    // Active sessions (unexpired, unrevoked refresh tokens) of the current user, newest first.
    // The one matching this request's refresh cookie is flagged as this device.
    @GetMapping("/me/sessions")
    public List<SessionResponse> listSessions(
            @RequestAttribute("userId") Long userId,
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken
    ) {
        return svc.listSessions(userId, refreshToken);
    }

    // Revokes one specific session. A no-op, not an error, if the id does not exist or is
    // already inactive, matching /logout.
    @DeleteMapping("/me/sessions/{id}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable("id") Long sessionId,
            @RequestAttribute("userId") Long userId
    ) {
        svc.revokeSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    // Updates both notification preference toggles for the current user.
    @PatchMapping("/me/notification-preferences")
    public ResponseEntity<UserResponse> updateNotificationPreferences(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody NotificationPreferencesRequest req
    ) {
        return ResponseEntity.ok(svc.updateNotificationPreferences(userId, req));
    }

    // A customer asks to become a seller. Only records a pending request; an admin approves it
    // (PUT /users/{id}/role) or rejects it (DELETE /users/{id}/role-request).
    @PostMapping("/me/become-seller")
    public ResponseEntity<UserResponse> becomeSeller(@RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(svc.becomeSeller(userId));
    }

    // Admin-only: assigns a role (USER / SELLER / ADMIN). Approves a pending seller request and
    // is the only way anyone becomes an admin.
    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleAssignmentRequest req,
            @RequestAttribute("userId") Long adminId,
            @RequestAttribute("userRole") String role
    ) {
        requireAdmin(role, "Only admins may assign roles");
        return ResponseEntity.ok(svc.assignRole(id, req.getRole(), adminId));
    }

    // Admin-only: declines a pending role request; the user keeps their current role.
    @DeleteMapping("/{id}/role-request")
    public ResponseEntity<UserResponse> rejectRoleRequest(
            @PathVariable Long id,
            @RequestAttribute("userRole") String role
    ) {
        requireAdmin(role, "Only admins may review role requests");
        return ResponseEntity.ok(svc.rejectRoleRequest(id));
    }

    private static void requireAdmin(String role, String message) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException(message);
        }
    }

    // Internal-only: notification-svc calls this with a SYSTEM token to decide whether an
    // order-status email should be sent. Not available to regular user tokens.
    @GetMapping("/{id}/notification-preferences")
    public NotificationPreferencesResponse getNotificationPreferences(
            @PathVariable("id") Long id,
            @RequestAttribute("userRole") String role
    ) {
        if (!"SYSTEM".equalsIgnoreCase(role)) {
            throw new ForbiddenException("This lookup is for internal service calls only");
        }
        return svc.getNotificationPreferences(id);
    }

    // Deletes a user by id; allowed for the account owner or an ADMIN.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") long id,
            @RequestAttribute("userId") Long requesterId,
            @RequestAttribute("userRole") String requesterRole
    ) {
        boolean deleted = svc.deleteById(id, requesterId, requesterRole);
        if (!deleted)
            return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
