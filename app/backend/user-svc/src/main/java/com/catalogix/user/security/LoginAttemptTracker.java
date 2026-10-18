package com.catalogix.user.security;

import com.catalogix.user.exception.AccountLockedException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory brute-force guard for /users/login, keyed by email. This is
 * per-instance state (not shared across replicas) — acceptable for a single
 * instance or small deployment; a multi-instance deployment would want this
 * backed by something shared (e.g. Redis) instead.
 */
@Component
public class LoginAttemptTracker {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration ENTRY_TTL = Duration.ofHours(1);

    private static class Attempt {
        final AtomicInteger failures = new AtomicInteger(0);
        volatile Instant lockedUntil;
        volatile Instant lastSeen = Instant.now();
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void assertNotLocked(String email) {
        Attempt attempt = attempts.get(key(email));
        if (attempt == null || attempt.lockedUntil == null) {
            return;
        }
        Instant now = Instant.now();
        if (attempt.lockedUntil.isAfter(now)) {
            throw new AccountLockedException(Duration.between(now, attempt.lockedUntil));
        }
    }

    public void recordFailure(String email) {
        Attempt attempt = attempts.computeIfAbsent(key(email), k -> new Attempt());
        attempt.lastSeen = Instant.now();
        int failures = attempt.failures.incrementAndGet();
        if (failures >= MAX_ATTEMPTS) {
            attempt.lockedUntil = Instant.now().plus(LOCK_DURATION);
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    // Bounds memory growth: without this, every distinct email ever tried
    // (including typos/attacker-guessed addresses) would sit in the map forever.
    @Scheduled(fixedDelay = 30 * 60 * 1000)
    public void cleanupStaleEntries() {
        Instant cutoff = Instant.now().minus(ENTRY_TTL);
        attempts.entrySet().removeIf(e ->
                e.getValue().lastSeen.isBefore(cutoff)
                        && (e.getValue().lockedUntil == null || e.getValue().lockedUntil.isBefore(Instant.now())));
    }
}
