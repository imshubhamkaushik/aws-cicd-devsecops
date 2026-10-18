package com.catalogix.user.security;

import com.catalogix.user.exception.UnauthorizedException;
import com.catalogix.user.model.RefreshToken;
import com.catalogix.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final long WEEK_MS = Duration.ofDays(7).toMillis();
    private static final long MONTH_MS = Duration.ofDays(30).toMillis();

    @Mock private RefreshTokenRepository repo;
    @Mock private TokenHasher hasher;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RefreshTokenService(repo, hasher, WEEK_MS, MONTH_MS);
        when(hasher.hash(anyString())).thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
        when(hasher.generateRawToken()).thenReturn("new-raw-token");
    }

    private RefreshToken activeToken(long id, Instant sessionStartedAt) {
        RefreshToken t = new RefreshToken(7L, "hash(old-raw-token)", Instant.now().plusSeconds(3600), "agent");
        t.setId(id);
        t.setSessionStartedAt(sessionStartedAt);
        return t;
    }

    @Test
    void rotateClaimsTheTokenAtomicallyAndCarriesTheSessionStartForward() {
        Instant started = Instant.now().minus(Duration.ofDays(3));
        when(repo.findByTokenHash("hash(old-raw-token)")).thenReturn(Optional.of(activeToken(11L, started)));
        when(repo.revokeIfActive(11L)).thenReturn(1);

        RefreshTokenService.RotationResult result = service.rotate("old-raw-token");

        assertEquals(7L, result.userId());
        assertEquals("new-raw-token", result.newRefreshToken());
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(saved.capture());
        assertEquals(started, saved.getValue().getSessionStartedAt());
    }

    @Test
    void rotateRejectsATokenThatAnotherRequestAlreadyClaimed() {
        // Two simultaneous refreshes with the same token: the loser's UPDATE matches 0 rows.
        when(repo.findByTokenHash("hash(old-raw-token)")).thenReturn(Optional.of(activeToken(11L, Instant.now())));
        when(repo.revokeIfActive(11L)).thenReturn(0);

        assertThrows(UnauthorizedException.class, () -> service.rotate("old-raw-token"));
        verify(repo, never()).save(any());
    }

    @Test
    void rotateRejectsASessionOlderThanTheAbsoluteLimit_eventIfTheTokenItselfIsFresh() {
        Instant startedTooLongAgo = Instant.now().minus(Duration.ofDays(31));
        when(repo.findByTokenHash("hash(old-raw-token)")).thenReturn(Optional.of(activeToken(11L, startedTooLongAgo)));
        when(repo.revokeIfActive(11L)).thenReturn(1);

        assertThrows(UnauthorizedException.class, () -> service.rotate("old-raw-token"));
        verify(repo, never()).save(any());
    }

    @Test
    void rotateRejectsAnUnknownToken() {
        when(repo.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.rotate("nope"));
    }

    @Test
    void revokeAllForUserExceptKeepsTheGivenSession() {
        service.revokeAllForUserExcept(7L, "keep-me");

        verify(repo).revokeAllForUserExcept(7L, "hash(keep-me)");
        verify(repo, never()).revokeAllForUser(anyLong());
    }

    @Test
    void revokeAllForUserExceptWithoutATokenRevokesEverything() {
        service.revokeAllForUserExcept(7L, null);

        verify(repo).revokeAllForUser(7L);
        verify(repo, never()).revokeAllForUserExcept(anyLong(), anyString());
    }
}
