package com.catalogix.user.svc;

import com.catalogix.user.dto.AuthResponse;
import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.NotificationPreferencesRequest;
import com.catalogix.user.dto.NotificationPreferencesResponse;
import com.catalogix.user.dto.SessionResponse;
import com.catalogix.user.dto.TokenPairResponse;
import com.catalogix.user.dto.UpdateProfileRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.exception.ForbiddenException;
import com.catalogix.user.event.EmailVerificationRequestedEvent;
import com.catalogix.user.event.PasswordResetRequestedEvent;
import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.user.exception.UserNotFoundException;
import com.catalogix.user.model.EmailVerificationToken;
import com.catalogix.user.model.PasswordResetToken;
import com.catalogix.user.model.User;
import com.catalogix.user.repository.EmailVerificationTokenRepository;
import com.catalogix.user.repository.PasswordResetTokenRepository;
import com.catalogix.user.repository.UserRepository;
import com.catalogix.user.security.UserJwtService;
import com.catalogix.user.security.LoginAttemptTracker;
import com.catalogix.user.security.RefreshTokenService;
import com.catalogix.user.security.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Business logic for users:
 * - register (hash password, issue access + refresh tokens, send a verification email)
 * - login (verify password with brute-force lockout, issue tokens)
 * - refresh (rotate a refresh token for a new access token)
 * - forgotPassword / resetPassword, verifyEmail, updateProfile
 * - listAll / findById (return DTOs)
 * - deleteById (self or ADMIN only)
 */
@Service
public class UserSvc {

    private static final long EMAIL_VERIFICATION_TTL_MS = 24L * 60 * 60 * 1000; // 24h
    private static final long PASSWORD_RESET_TTL_MS = 60L * 60 * 1000; // 1h
    private static final String ROLE_USER = "USER";
    private static final String ROLE_SELLER = "SELLER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final UserJwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptTracker loginAttemptTracker;
    private final EmailVerificationTokenRepository emailVerificationRepo;
    private final PasswordResetTokenRepository passwordResetRepo;
    private final TokenHasher tokenHasher;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;
    // Hash of a random value, computed lazily; only compared against to equalise login
    // timing for unknown emails (see loginInternal).
    private volatile String dummyPasswordHash;
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(ROLE_USER, ROLE_SELLER, ROLE_ADMIN);
    private static final String ACCOUNT_NO_LONGER_EXISTS = "Account no longer exists";

    public UserSvc(
            UserRepository repo,
            PasswordEncoder passwordEncoder,
            UserJwtService jwtService,
            RefreshTokenService refreshTokenService,
            LoginAttemptTracker loginAttemptTracker,
            EmailVerificationTokenRepository emailVerificationRepo,
            PasswordResetTokenRepository passwordResetRepo,
            TokenHasher tokenHasher,
            ApplicationEventPublisher eventPublisher,
            @Value("${FRONTEND_BASE_URL:http://localhost:11000}") String frontendBaseUrl) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptTracker = loginAttemptTracker;
        this.emailVerificationRepo = emailVerificationRepo;
        this.passwordResetRepo = passwordResetRepo;
        this.tokenHasher = tokenHasher;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    // Registers a new user. @Transactional keeps the email-exists check and the save in one
    // transaction so concurrent requests cannot register the same email.
    @Transactional
    public AuthResponse register(CreateUserRequest req) {
        return registerInternal(req, null);
    }

    @Transactional
    public AuthResponse register(CreateUserRequest req, String userAgent) {
        return registerInternal(req, userAgent);
    }

    private AuthResponse registerInternal(CreateUserRequest req, String userAgent) {
        validatePassword(req.getPassword());
        if (repo.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        // Everyone who registers is a customer; higher roles are assigned only by an admin (see assignRole).
        user.setRole("USER");

        User saved = repo.save(user);
        sendVerificationEmail(saved);
        return issueAuthResponse(saved, userAgent);
    }

    private String dummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
            dummyPasswordHash = hash;
        }
        return hash;
    }

    // Logs a user in and returns fresh tokens plus the profile. After MAX_ATTEMPTS consecutive
    // failures for an email, further attempts are rejected for a cool-down window, even with the
    // correct password.
    @Transactional
    public AuthResponse login(LoginRequest req) {
        return loginInternal(req, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String userAgent) {
        return loginInternal(req, userAgent);
    }

    private AuthResponse loginInternal(LoginRequest req, String userAgent) {
        loginAttemptTracker.assertNotLocked(req.getEmail());

        User user = repo.findByEmail(req.getEmail()).orElse(null);
        boolean passwordOk;
        if (user != null) {
            passwordOk = passwordEncoder.matches(req.getPassword(), user.getPassword());
        } else {
            // Unknown email: do the same amount of hashing work anyway. Without this the
            // response is measurably faster than for a real account, which lets an
            // attacker enumerate registered emails by timing the login endpoint.
            passwordEncoder.matches(req.getPassword(), dummyPasswordHash());
            passwordOk = false;
        }

        if (!passwordOk) {
            loginAttemptTracker.recordFailure(req.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }

        loginAttemptTracker.recordSuccess(req.getEmail());
        return issueAuthResponse(user, userAgent);
    }

    // Exchanges a valid refresh token for a new access token and a rotated refresh token.
    @Transactional
    public TokenPairResponse refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(refreshToken);
        User user = repo.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));

        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenPairResponse(accessToken, jwtService.getExpirationMs(), rotation.newRefreshToken());
    }

    // Revoke a single refresh token (log out this session only).
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    // Revoke every refresh token belonging to a user (log out everywhere).
    public void logoutEverywhere(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    // Sends (or re-sends) the verification link. Publishing an event is best-effort, so this
    // never blocks registration when RabbitMQ or notification-svc is unavailable.
    private void sendVerificationEmail(User user) {
        String rawToken = tokenHasher.generateRawToken();
        emailVerificationRepo.save(new EmailVerificationToken(
                user.getId(), tokenHasher.hash(rawToken), Instant.now().plusMillis(EMAIL_VERIFICATION_TTL_MS)));

        String link = frontendBaseUrl + "/verify-email?token=" + rawToken;
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(user.getEmail(), user.getName(), link));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationRepo.findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired verification link"));
        if (!token.isValid(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired verification link");
        }

        User user = repo.findById(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));
        user.setVerified(true);
        repo.save(user);

        token.setUsed(true);
        emailVerificationRepo.save(token);
    }

    // For when the original verification email was lost or expired. No-op if already verified.
    @Transactional
    public void resendVerificationEmail(Long userId) {
        User user = repo.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));
        if (!user.isVerified()) {
            sendVerificationEmail(user);
        }
    }

    // Never reveals whether the email exists, to prevent account enumeration.
    @Transactional
    public void forgotPassword(String email) {
        repo.findByEmail(email).ifPresent(user -> {
            String rawToken = tokenHasher.generateRawToken();
            passwordResetRepo.save(new PasswordResetToken(
                    user.getId(), tokenHasher.hash(rawToken), Instant.now().plusMillis(PASSWORD_RESET_TTL_MS)));

            String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
            eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getEmail(), user.getName(), link));
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        PasswordResetToken token = passwordResetRepo.findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired reset link"));
        if (!token.isValid(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired reset link");
        }

        User user = repo.findById(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));
        user.setPassword(passwordEncoder.encode(newPassword));
        repo.save(user);

        token.setUsed(true);
        passwordResetRepo.save(token);

        // Invalidate sessions so a refresh token stolen before the reset stops working.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    // Updates name/email/password for the current user. Changing email or
    // password requires currentPassword as a lightweight re-auth check.
    // Changing email resets verified to false and re-sends a verification email.
    @Transactional
    public UserResponse updateProfile(
            Long userId,
            UpdateProfileRequest req) {
        return updateProfileInternal(userId, req, null);
    }

    @Transactional
    public UserResponse updateProfile(
            Long userId,
            UpdateProfileRequest req,
            String currentRefreshToken) {
        return updateProfileInternal(
                userId,
                req,
                currentRefreshToken);
    }

    private UserResponse updateProfileInternal(
            Long userId,
            UpdateProfileRequest req,
            String currentRefreshToken) {
        // Validate the password policy first, so an invalid password is rejected before any lookup or mutation.
        boolean changingPassword = StringUtils.hasText(req.getNewPassword());

        if (changingPassword) {
            validatePassword(req.getNewPassword());
        }

        User user = repo.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));

        boolean changingEmail = StringUtils.hasText(req.getEmail())
                && !req.getEmail().equalsIgnoreCase(user.getEmail());

        if (changingEmail || changingPassword) {
            boolean currentPasswordOk = StringUtils.hasText(req.getCurrentPassword())
                    && passwordEncoder.matches(
                            req.getCurrentPassword(),
                            user.getPassword());

            if (!currentPasswordOk) {
                throw new UnauthorizedException(
                        "currentPassword is required and must be correct "
                                + "to change email or password");
            }
        }

        if (StringUtils.hasText(req.getName())) {
            user.setName(req.getName());
        }

        if (changingEmail) {
            if (repo.findByEmail(req.getEmail()).isPresent()) {
                throw new IllegalArgumentException(
                        "Email already registered");
            }

            user.setEmail(req.getEmail());
            user.setVerified(false);
        }

        if (changingPassword) {
            user.setPassword(
                    passwordEncoder.encode(req.getNewPassword()));

            refreshTokenService.revokeAllForUserExcept(
                    userId,
                    currentRefreshToken);
        }

        User saved = repo.save(user);

        if (changingEmail) {
            sendVerificationEmail(saved);
        }

        return toResponse(saved);
    }

    private void validatePassword(String password) {
        if (password == null || !password.matches("^(?=.*[A-Za-z])(?=.*\\d).{6,}$")) {
            throw new IllegalArgumentException(
                    "Password must be at least 6 characters and include a letter and a number");
        }
    }

    // Lists all users as DTOs (no passwords). Admin-only, enforced by the controller.
    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return repo.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return repo.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    // Deletes a user by id; only the user themselves or an ADMIN may do this. @Transactional keeps
    // the existence check and delete atomic.
    @Transactional
    public boolean deleteById(Long id, Long requesterId, String requesterRole) {
        if (id == null) {
            return false;
        }
        boolean isSelf = id.equals(requesterId);
        boolean isAdmin = ROLE_ADMIN.equalsIgnoreCase(requesterRole);
        if (!isSelf && !isAdmin) {
            throw new ForbiddenException("You may only delete your own account");
        }
        if (!repo.existsById(id)) {
            return false;
        }
        refreshTokenService.revokeAllForUser(id);
        repo.deleteById(id);
        return true;
    }

    private AuthResponse issueAuthResponse(User user, String userAgent) {
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId(), userAgent);
        return new AuthResponse(accessToken, jwtService.getExpirationMs(), refreshToken, toResponse(user));
    }

    private UserResponse toResponse(User u) {
        UserResponse response = new UserResponse();

        response.setId(u.getId());
        response.setName(u.getName());
        response.setEmail(u.getEmail());
        response.setRole(u.getRole());
        response.setVerified(u.isVerified());
        response.setCreatedAt(u.getCreatedAt());
        response.setOrderEmailsEnabled(u.isOrderEmailsEnabled());
        response.setRequestedRole(u.getRequestedRole());

        return response;
    }

    // ---- Sessions (see RefreshTokenService for the underlying storage) ----

    // currentRawToken is the raw refresh token from this request's cookie (may be null). It is
    // used only to flag which session is "this device" and is never returned to the client.
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(Long userId, String currentRawToken) {
        String currentHash = currentRawToken != null && !currentRawToken.isBlank()
                ? tokenHasher.hash(currentRawToken)
                : null;

        return refreshTokenService.listActiveSessions(userId).stream()
                .map(t -> new SessionResponse(
                        t.getId(), t.getUserAgent(), t.getCreatedAt(), t.getLastUsedAt(), t.getExpiresAt(),
                        t.getTokenHash().equals(currentHash)))
                .toList();
    }

    // Ownership is checked inside RefreshTokenService.revokeById.
    public void revokeSession(Long sessionId, Long userId) {
        refreshTokenService.revokeById(sessionId, userId);
    }

    // A customer asks to become a seller. Nothing is granted here; it only records a pending
    // request for an admin to approve (assignRole) or decline (rejectRoleRequest). Idempotent,
    // and a no-op for an existing SELLER or ADMIN so an admin is never downgraded.
    @Transactional
    public UserResponse becomeSeller(Long userId) {
        User user = repo.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));
        if (ROLE_USER.equalsIgnoreCase(user.getRole()) && user.getRequestedRole() == null) {
            user.setRequestedRole(ROLE_SELLER);
            user.setRoleRequestedAt(Instant.now());
            repo.save(user);
        }
        return toResponse(user);
    }

    // Admin action: assigns a role (USER, SELLER or ADMIN); this also approves a pending seller
    // request. Any pending request is cleared, so assigning USER to a requester is a rejection.
    // Guard rails: an admin cannot change their own role, and the last admin cannot be demoted.
    // A demotion also revokes the user's refresh tokens so they cannot mint tokens with the old
    // role (an issued access token lives until it expires, at most JWT_EXPIRATION_MS).
    @Transactional
    public UserResponse assignRole(Long targetUserId, String newRole, Long adminId) {
        if (newRole == null || !ASSIGNABLE_ROLES.contains(newRole.toUpperCase())) {
            throw new IllegalArgumentException("Unknown role: " + newRole);
        }
        String role = newRole.toUpperCase();
        if (targetUserId.equals(adminId)) {
            throw new ForbiddenException("You cannot change your own role");
        }
        User user = repo.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        String previous = user.getRole();
        
        boolean demotingAdmin = ROLE_ADMIN.equalsIgnoreCase(previous) && !ROLE_ADMIN.equals(role);
        
        if (demotingAdmin && repo.findAllByRoleForUpdate(ROLE_ADMIN).size() <= 1) {
            throw new IllegalArgumentException("Cannot remove the last admin");
        }

        user.setRole(role);
        user.setRequestedRole(null);
        user.setRoleRequestedAt(null);
        
        User saved = repo.save(user);

        if (isDemotion(previous, role)) {
            refreshTokenService.revokeAllForUser(targetUserId);
        }
        return toResponse(saved);
    }

    // Admin action: declines a pending role request without changing the user's role.
    @Transactional
    public UserResponse rejectRoleRequest(Long targetUserId) {
        User user = repo.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
        user.setRequestedRole(null);
        user.setRoleRequestedAt(null);
        return toResponse(repo.save(user));
    }

    private static boolean isDemotion(String previous, String next) {
        return roleRank(next) < roleRank(previous);
    }

    private static int roleRank(String role) {
        if (ROLE_ADMIN.equalsIgnoreCase(role)) {
            return 2;
        }
        return ROLE_SELLER.equalsIgnoreCase(role) ? 1 : 0;
    }

    // ---- Notification preferences ----
    // Enforced by notification-svc before it sends an order-status email.
    @Transactional
    public UserResponse updateNotificationPreferences(Long userId, NotificationPreferencesRequest req) {
        User user = repo.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ACCOUNT_NO_LONGER_EXISTS));
        user.setOrderEmailsEnabled(req.isOrderEmailsEnabled());
        return toResponse(repo.save(user));
    }

    // Internal lookup for notification-svc (SYSTEM role only). Defaults to true/true for an
    // unknown user, so a deleted-account race never silently drops a legitimate notification.
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getNotificationPreferences(Long userId) {
        return repo.findById(userId)
                .map(u -> new NotificationPreferencesResponse(u.isOrderEmailsEnabled()))
                .orElse(new NotificationPreferencesResponse(true));
    }
}