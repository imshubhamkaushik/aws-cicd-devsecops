package com.catalogix.user.exception;

import java.time.Duration;

public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(Duration retryAfter) {
        super("Too many failed login attempts. Try again in "
                + Math.max(1, retryAfter.toSeconds()) + " seconds.");
        this.retryAfterSeconds = Math.max(1, retryAfter.toSeconds());
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
