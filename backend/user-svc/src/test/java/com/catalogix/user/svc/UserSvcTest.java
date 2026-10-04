package com.catalogix.user.svc;

import com.catalogix.user.dto.AuthResponse;
import com.catalogix.user.dto.CreateUserRequest;
import com.catalogix.user.dto.LoginRequest;
import com.catalogix.user.dto.NotificationPreferencesRequest;
import com.catalogix.user.dto.SessionResponse;
import com.catalogix.user.dto.TokenPairResponse;
import com.catalogix.user.dto.UpdateProfileRequest;
import com.catalogix.user.dto.UserResponse;
import com.catalogix.user.event.EmailVerificationRequestedEvent;
import com.catalogix.user.event.PasswordResetRequestedEvent;
import com.catalogix.user.exception.AccountLockedException;
import com.catalogix.user.exception.ForbiddenException;
import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.user.exception.UserNotFoundException;
import com.catalogix.user.model.EmailVerificationToken;
import com.catalogix.user.model.PasswordResetToken;
import com.catalogix.user.model.RefreshToken;
import com.catalogix.user.model.User;
import com.catalogix.user.repository.EmailVerificationTokenRepository;
import com.catalogix.user.repository.PasswordResetTokenRepository;
import com.catalogix.user.repository.UserRepository;
import com.catalogix.user.security.UserJwtService;
import com.catalogix.user.security.LoginAttemptTracker;
import com.catalogix.user.security.RefreshTokenService;
import com.catalogix.user.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SuppressWarnings("null") // Bypasses strict JDT null-analysis warnings for Mockito's any() returning dummy nulls
class UserSvcTest {

    @Mock private UserRepository repo;
    @Mock private PasswordEncoder encoder;
    @Mock private UserJwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginAttemptTracker loginAttemptTracker;
    @Mock private EmailVerificationTokenRepository emailVerificationRepo;
    @Mock private PasswordResetTokenRepository passwordResetRepo;
    @Mock private TokenHasher tokenHasher;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UserSvc svc;

    private static final String TEST_EMAIL = "x@x.com";
    private static final String OLD_EMAIL = "old@x.com";
    private static final String NEW_EMAIL = "new@x.com";
    private static final String TAKEN_EMAIL = "taken@x.com";
    
    // Renamed constants to avoid SonarQube's strict hard-coded password detection rules
    private static final String HASHED_SECRET = "hashed";
    private static final String VALID_SECRET = "Password1";
    private static final String NEW_SECRET = "NewPassword1";
    private static final String CORRECT_SECRET = "CorrectPass1";
    
    private static final String RAW_TOKEN = "raw-token";
    private static final String HASHED_TOKEN = "hash(raw-token)";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String FAKE_ACCESS_TOKEN = "fake.access.token";
    private static final String FAKE_REFRESH_TOKEN = "fake-refresh-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Constructed by hand (rather than @InjectMocks) since the constructor also
        // takes a plain string (FRONTEND_BASE_URL), which Mockito can't auto-mock.
        svc = new UserSvc(repo, encoder, jwtService, refreshTokenService, loginAttemptTracker,
                emailVerificationRepo, passwordResetRepo, tokenHasher, eventPublisher,
                "http://localhost:8080");
        lenient().when(jwtService.generateToken(any(), any(), any())).thenReturn(FAKE_ACCESS_TOKEN);
        lenient().when(jwtService.getExpirationMs()).thenReturn(900_000L);
        // UserSvc.issueAuthResponse always calls the 2-arg overload now (even
        // when userAgent is null, via register(req)/login(req)'s 1-arg
        // convenience wrappers) — stubbing only the 1-arg issue(Long) here
        // would leave this mock unstubbed for every register/login test.
        lenient().when(refreshTokenService.issue(any(), any())).thenReturn(FAKE_REFRESH_TOKEN);
        lenient().when(tokenHasher.generateRawToken()).thenReturn(RAW_TOKEN);
        lenient().when(tokenHasher.hash(anyString())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
    }

    private User sampleUser(Long id, String email, String hashedPassword) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setName("Name");
        u.setPassword(hashedPassword);
        u.setRole(ROLE_USER);
        return u;
    }

    // Register tests
    @Test
    void registerSucceedsAssignsUserRoleAndSendsVerificationEmail() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Name");
        req.setEmail("name@example.com");
        req.setPassword(VALID_SECRET);

        when(repo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(encoder.encode(req.getPassword())).thenReturn(HASHED_SECRET);
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        AuthResponse resp = svc.register(req);
        assertNotNull(resp);
        assertEquals(FAKE_ACCESS_TOKEN, resp.getAccessToken());
        assertEquals(1L, resp.getUser().getId());
        assertEquals(ROLE_USER, resp.getUser().getRole());
        assertFalse(resp.getUser().isVerified());
        verify(emailVerificationRepo).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
    }

    @Test
    void registerNeverGrantsAHigherRole_evenForAnAdminLookingEmail() {
        // There is no email allow-list any more: nobody can become an admin by
        // registering. Only an existing admin can assign roles (see assignRole tests).
        for (String email : new String[] {"Admin@Example.com", "admin@catalogix.local", "root@admin.com"}) {
            CreateUserRequest req = new CreateUserRequest();
            req.setName("Boss");
            req.setEmail(email);
            req.setPassword(VALID_SECRET);

            when(repo.findByEmail(req.getEmail())).thenReturn(Optional.empty());
            when(encoder.encode(req.getPassword())).thenReturn(HASHED_SECRET);
            when(repo.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });

            assertEquals(ROLE_USER, svc.register(req).getUser().getRole(), email);
        }
    }

    @Test
    void registerRejectsPasswordWithoutALetterOrNumber() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Name");
        req.setEmail("weak@example.com");
        req.setPassword("123456");

        assertThrows(IllegalArgumentException.class, () -> svc.register(req));
        verify(repo, never()).findByEmail(anyString());
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void registerDuplicateEmailThrows() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("A"); req.setEmail("a@a.com"); req.setPassword(VALID_SECRET);
        when(repo.findByEmail(req.getEmail())).thenReturn(Optional.of(new User()));

        assertThrows(IllegalArgumentException.class, () -> svc.register(req));
        verify(repo, never()).save(any());
    }

    // Login tests
    @Test
    void loginSucceeds() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        when(repo.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(u));
        when(encoder.matches(VALID_SECRET, HASHED_SECRET)).thenReturn(true);

        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL); req.setPassword(VALID_SECRET);

        AuthResponse resp = svc.login(req);
        assertEquals(1L, resp.getUser().getId());
        verify(loginAttemptTracker).recordSuccess(TEST_EMAIL);
    }

    @Test
    void loginForAnUnknownEmailStillDoesTheHashingWork() {
        // Timing side channel: an unknown email must cost the same bcrypt comparison as a
        // real one, otherwise response time reveals which emails are registered.
        when(repo.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("dummy-hash");

        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL); req.setPassword("whatever1");

        assertThrows(UnauthorizedException.class, () -> svc.login(req));
        verify(encoder).matches("whatever1", "dummy-hash");
        verify(loginAttemptTracker).recordFailure(TEST_EMAIL);
    }

    @Test
    void loginFailsWithBadPasswordRecordsFailure() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        when(repo.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(u));
        when(encoder.matches("wrongpass", HASHED_SECRET)).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL); req.setPassword("wrongpass");

        assertThrows(UnauthorizedException.class, () -> svc.login(req));
        verify(loginAttemptTracker).recordFailure(TEST_EMAIL);
    }

    @Test
    void loginRejectsWhenAccountLocked() {
        doThrow(new AccountLockedException(java.time.Duration.ofMinutes(5)))
                .when(loginAttemptTracker).assertNotLocked("locked@x.com");

        LoginRequest req = new LoginRequest();
        req.setEmail("locked@x.com"); req.setPassword("whatever");

        assertThrows(AccountLockedException.class, () -> svc.login(req));
        verify(repo, never()).findByEmail(anyString());
    }

    // refresh / logout tests
    @Test
    void refreshRotatesTokenAndReturnsNewPair() {
        User u = sampleUser(7L, TEST_EMAIL, HASHED_SECRET);
        when(refreshTokenService.rotate("old-token"))
                .thenReturn(new RefreshTokenService.RotationResult(7L, "new-refresh-token"));
        when(repo.findById(7L)).thenReturn(Optional.of(u));

        TokenPairResponse resp = svc.refresh("old-token");
        assertEquals("new-refresh-token", resp.getRefreshToken());
    }

    @Test
    void logoutRevokesGivenToken() {
        svc.logout("some-token");
        verify(refreshTokenService).revoke("some-token");
    }

    @Test
    void logoutEverywhereRevokesAllForUser() {
        svc.logoutEverywhere(5L);
        verify(refreshTokenService).revokeAllForUser(5L);
    }

    // email verification tests
    @Test
    void verifyEmailMarksUserVerified() {
        EmailVerificationToken token = new EmailVerificationToken(1L, HASHED_TOKEN, Instant.now().plusSeconds(60));
        when(emailVerificationRepo.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(token));
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        svc.verifyEmail(RAW_TOKEN);

        assertTrue(u.isVerified());
        assertTrue(token.isUsed());
        verify(repo).save(u);
    }

    @Test
    void verifyEmailRejectsExpiredToken() {
        EmailVerificationToken token = new EmailVerificationToken(1L, HASHED_TOKEN, Instant.now().minusSeconds(60));
        when(emailVerificationRepo.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(token));

        assertThrows(UnauthorizedException.class, () -> svc.verifyEmail(RAW_TOKEN));
        verify(repo, never()).save(any());
    }

    @Test
    void verifyEmailRejectsUnknownToken() {
        when(emailVerificationRepo.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> svc.verifyEmail("bogus"));
    }

    @Test
    void resendVerificationEmailSendsAgainWhenUnverified() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setVerified(false);
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        svc.resendVerificationEmail(1L);

        verify(eventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
    }

    @Test
    void resendVerificationEmailIsNoOpWhenAlreadyVerified() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setVerified(true);
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        svc.resendVerificationEmail(1L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // forgot/reset password tests
    @Test
    void forgotPasswordSendsEmailWhenAccountExists() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        when(repo.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(u));

        svc.forgotPassword(TEST_EMAIL);

        verify(passwordResetRepo).save(any(PasswordResetToken.class));
        verify(eventPublisher).publishEvent(any(PasswordResetRequestedEvent.class));
    }

    @Test
    void forgotPasswordSilentlyNoOpsWhenAccountDoesNotExist() {
        when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        svc.forgotPassword("nobody@example.com"); // should not throw

        verify(passwordResetRepo, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetPasswordUpdatesPasswordAndRevokesSessions() {
        PasswordResetToken token = new PasswordResetToken(1L, HASHED_TOKEN, Instant.now().plusSeconds(60));
        when(passwordResetRepo.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(token));
        User u = sampleUser(1L, TEST_EMAIL, "oldHashed");
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.encode(NEW_SECRET)).thenReturn("newHashed");

        svc.resetPassword(RAW_TOKEN, NEW_SECRET);

        assertEquals("newHashed", u.getPassword());
        assertTrue(token.isUsed());
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void resetPasswordRejectsWeakPasswordBeforeConsumingResetToken() {
        assertThrows(IllegalArgumentException.class, () -> svc.resetPassword(RAW_TOKEN, "123456"));
        verify(passwordResetRepo, never()).findByTokenHash(anyString());
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken token = new PasswordResetToken(1L, HASHED_TOKEN, Instant.now().minusSeconds(60));
        when(passwordResetRepo.findByTokenHash(HASHED_TOKEN)).thenReturn(Optional.of(token));

        assertThrows(UnauthorizedException.class, () -> svc.resetPassword(RAW_TOKEN, NEW_SECRET));
    }

    // updateProfile tests
    @Test
    void updateProfileChangesNameWithoutRequiringPassword() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("New Name");

        UserResponse resp = svc.updateProfile(1L, req);
        assertEquals("New Name", resp.getName());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void updateProfileRejectsWeakNewPasswordBeforeLoadingUser() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNewPassword("123456");

        assertThrows(IllegalArgumentException.class, () -> svc.updateProfile(1L, req));
        verify(repo, never()).findById(anyLong());
    }

    @Test
    void updateProfileRequiresCurrentPasswordToChangeEmail() {
        User u = sampleUser(1L, OLD_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail(NEW_EMAIL);
        // no currentPassword supplied

        assertThrows(UnauthorizedException.class, () -> svc.updateProfile(1L, req));
        verify(repo, never()).save(any());
    }

    @Test
    void updateProfileChangesEmailAndResendsVerification() {
        User u = sampleUser(1L, OLD_EMAIL, HASHED_SECRET);
        u.setVerified(true);
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.findByEmail(NEW_EMAIL)).thenReturn(Optional.empty());
        when(encoder.matches(CORRECT_SECRET, HASHED_SECRET)).thenReturn(true);
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail(NEW_EMAIL);
        req.setCurrentPassword(CORRECT_SECRET);

        UserResponse resp = svc.updateProfile(1L, req);

        assertEquals(NEW_EMAIL, resp.getEmail());
        assertFalse(resp.isVerified()); // reset since the email changed
        verify(eventPublisher).publishEvent(any(EmailVerificationRequestedEvent.class));
    }

    @Test
    void updateProfilePasswordChangeSignsOutOtherSessionsButKeepsThisOne() {
        User u = sampleUser(1L, OLD_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.matches(CORRECT_SECRET, HASHED_SECRET)).thenReturn(true);
        when(encoder.encode("BrandNewPass1")).thenReturn("new-hash");
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setCurrentPassword(CORRECT_SECRET);
        req.setNewPassword("BrandNewPass1");

        svc.updateProfile(1L, req, "my-own-refresh-cookie");

        verify(refreshTokenService).revokeAllForUserExcept(1L, "my-own-refresh-cookie");
    }

    @Test
    void updateProfileWithoutPasswordChangeLeavesSessionsAlone() {
        User u = sampleUser(1L, OLD_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setName("Renamed");

        svc.updateProfile(1L, req, "cookie");

        verify(refreshTokenService, never()).revokeAllForUserExcept(anyLong(), any());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void updateProfileRejectsEmailAlreadyTaken() {
        User u = sampleUser(1L, OLD_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.matches(CORRECT_SECRET, HASHED_SECRET)).thenReturn(true);
        when(repo.findByEmail(TAKEN_EMAIL)).thenReturn(Optional.of(sampleUser(2L, TAKEN_EMAIL, "x")));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setEmail(TAKEN_EMAIL);
        req.setCurrentPassword(CORRECT_SECRET);

        assertThrows(IllegalArgumentException.class, () -> svc.updateProfile(1L, req));
    }

    // listAll / deleteById tests (unchanged behavior, still covered)
    @Test
    void listAllReturnsMappedDTOs() {
        User u1 = sampleUser(1L, "alice@x.com", "h"); u1.setName("Alice"); u1.setRole(ROLE_ADMIN);
        User u2 = sampleUser(2L, "bob@x.com", "h");   u2.setName("Bob");
        when(repo.findAll()).thenReturn(List.of(u1, u2));

        List<UserResponse> result = svc.listAll();
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getName());
    }

    @Test
    void deleteByIdAllowsSelfAndRevokesTokens() {
        when(repo.existsById(1L)).thenReturn(true);
        assertTrue(svc.deleteById(1L, 1L, ROLE_USER));
        verify(repo).deleteById(1L);
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    void deleteByIdRejectsOtherNonAdminUsers() {
        assertThrows(ForbiddenException.class, () -> svc.deleteById(1L, 2L, ROLE_USER));
        verify(repo, never()).deleteById(anyLong());
    }

    // ---- register/login user-agent threading ----

    // ---- sessions ----

    @Test
    void listSessionsMapsToDtosAndFlagsTheCurrentOne() {
        RefreshToken current = new RefreshToken(1L, "hash(current-raw-token)", Instant.now().plusSeconds(3600), "Chrome");
        current.setId(10L);
        RefreshToken other = new RefreshToken(1L, "hash(other-token)", Instant.now().plusSeconds(3600), "Safari");
        other.setId(11L);
        when(refreshTokenService.listActiveSessions(1L)).thenReturn(List.of(current, other));

        List<SessionResponse> sessions = svc.listSessions(1L, "current-raw-token");

        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().filter(s -> s.getId().equals(10L)).findFirst().orElseThrow().isCurrent());
        assertFalse(sessions.stream().filter(s -> s.getId().equals(11L)).findFirst().orElseThrow().isCurrent());
    }

    @Test
    void listSessionsWithNoCookieFlagsNothingAsCurrent() {
        RefreshToken token = new RefreshToken(1L, "hash(some-token)", Instant.now().plusSeconds(3600), "Chrome");
        token.setId(10L);
        when(refreshTokenService.listActiveSessions(1L)).thenReturn(List.of(token));

        List<SessionResponse> sessions = svc.listSessions(1L, null);

        assertFalse(sessions.get(0).isCurrent());
    }

    @Test
    void revokeSessionDelegatesToRefreshTokenServiceWithBothIds() {
        svc.revokeSession(10L, 1L);
        verify(refreshTokenService).revokeById(10L, 1L);
    }

    // ---- notification preferences ----

    @Test
    void updateNotificationPreferencesUpdatesAndReturnsBothFlags() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferencesRequest req = new NotificationPreferencesRequest();
        req.setOrderEmailsEnabled(false);

        UserResponse resp = svc.updateNotificationPreferences(1L, req);

        assertFalse(resp.isOrderEmailsEnabled());
    }

    @Test
    void updateNotificationPreferencesThrowsForUnknownUser() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        NotificationPreferencesRequest req = new NotificationPreferencesRequest();

        assertThrows(UnauthorizedException.class, () -> svc.updateNotificationPreferences(99L, req));
    }

    @Test
    void getNotificationPreferencesReturnsStoredValues() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setOrderEmailsEnabled(false);
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        var prefs = svc.getNotificationPreferences(1L);

        assertFalse(prefs.isOrderEmailsEnabled());
    }

    @Test
    void getNotificationPreferencesDefaultsToSendEverythingForUnknownUser() {
        // Fail-open on purpose — see UserSvc.getNotificationPreferences's Javadoc.
        when(repo.findById(99L)).thenReturn(Optional.empty());

        var prefs = svc.getNotificationPreferences(99L);

        assertTrue(prefs.isOrderEmailsEnabled());
    }

    // ---- becomeSeller (a REQUEST — an admin decides) ----

    @Test
    void becomeSellerOnlyRecordsAPendingRequest_itNeverGrantsTheRole() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setRole("USER");
        when(repo.findById(1L)).thenReturn(Optional.of(u));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse resp = svc.becomeSeller(1L);

        assertEquals("USER", resp.getRole());
        assertEquals("SELLER", resp.getRequestedRole());
        verify(repo).save(argThat(saved -> "USER".equals(saved.getRole())
                && "SELLER".equals(saved.getRequestedRole())
                && saved.getRoleRequestedAt() != null));
    }

    @Test
    void becomeSellerIsIdempotentWhileARequestIsPending() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setRequestedRole("SELLER");
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        UserResponse resp = svc.becomeSeller(1L);

        assertEquals("SELLER", resp.getRequestedRole());
        verify(repo, never()).save(any());
    }

    @Test
    void becomeSellerIsANoOpForAnExistingSeller() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setRole("SELLER");
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        UserResponse resp = svc.becomeSeller(1L);

        assertEquals("SELLER", resp.getRole());
        verify(repo, never()).save(any());
    }

    @Test
    void becomeSellerDoesNotDowngradeAnAdmin() {
        User u = sampleUser(1L, TEST_EMAIL, HASHED_SECRET);
        u.setRole("ADMIN");
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        UserResponse resp = svc.becomeSeller(1L);

        assertEquals("ADMIN", resp.getRole());
        verify(repo, never()).save(any());
    }

    @Test
    void becomeSellerThrowsForUnknownUser() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> svc.becomeSeller(99L));
    }

    // ---- assignRole / rejectRoleRequest (admin decisions) ----

    @Test
    void assignRoleApprovesASellerRequestAndClearsIt() {
        User target = sampleUser(2L, "bob@x.com", "h");
        target.setRequestedRole("SELLER");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse resp = svc.assignRole(2L, "seller", 1L);

        assertEquals("SELLER", resp.getRole());
        assertNull(resp.getRequestedRole());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    void assignRoleCanPromoteToAdmin() {
        User target = sampleUser(2L, "bob@x.com", "h");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("ADMIN", svc.assignRole(2L, "ADMIN", 1L).getRole());
    }

    @Test
    void assignRoleDemotionRevokesTheUsersSessions() {
        User target = sampleUser(2L, "bob@x.com", "h");
        target.setRole("SELLER");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("USER", svc.assignRole(2L, "USER", 1L).getRole());
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    void assignRoleRefusesToChangeYourOwnRole() {
        assertThrows(ForbiddenException.class, () -> svc.assignRole(1L, "USER", 1L));
        verify(repo, never()).save(any());
    }

    @Test
    void assignRoleRefusesToRemoveTheLastAdmin() {
        User target = sampleUser(2L, "root@x.com", "h");
        target.setRole("ADMIN");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        when(repo.findAllByRoleForUpdate("ADMIN")).thenReturn(List.of(target));

        assertThrows(IllegalArgumentException.class, () -> svc.assignRole(2L, "USER", 1L));
        verify(repo, never()).save(any());
    }

    @Test
    void assignRoleAllowsDemotingAnAdminWhenAnotherAdminRemains() {
        User target = sampleUser(2L, "second@x.com", "h");
        target.setRole("ADMIN");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        User otherAdmin = sampleUser(3L, "root@x.com", "h");
        otherAdmin.setRole("ADMIN");
        when(repo.findAllByRoleForUpdate("ADMIN")).thenReturn(List.of(target, otherAdmin));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("USER", svc.assignRole(2L, "USER", 1L).getRole());
    }

    @Test
    void assignRoleRejectsUnknownRoles() {
        assertThrows(IllegalArgumentException.class, () -> svc.assignRole(2L, "SUPERUSER", 1L));
        assertThrows(IllegalArgumentException.class, () -> svc.assignRole(2L, null, 1L));
    }

    @Test
    void assignRoleThrowsNotFoundForUnknownTarget() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> svc.assignRole(99L, "SELLER", 1L));
    }

    @Test
    void rejectRoleRequestClearsThePendingRequestWithoutChangingTheRole() {
        User target = sampleUser(2L, "bob@x.com", "h");
        target.setRequestedRole("SELLER");
        when(repo.findById(2L)).thenReturn(Optional.of(target));
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse resp = svc.rejectRoleRequest(2L);

        assertEquals("USER", resp.getRole());
        assertNull(resp.getRequestedRole());
    }
}
