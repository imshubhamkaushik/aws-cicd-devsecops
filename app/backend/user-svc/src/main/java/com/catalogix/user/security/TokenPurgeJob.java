package com.catalogix.user.security;

import com.catalogix.user.repository.EmailVerificationTokenRepository;
import com.catalogix.user.repository.PasswordResetTokenRepository;
import com.catalogix.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Housekeeping: deletes refresh, email-verification and password-reset tokens long past
 * their usefulness. Runs daily (cron overridable with TOKEN_PURGE_CRON); running on every
 * replica is harmless since deleting already-deleted rows is a no-op. Tokens are retained
 * for a period after expiry so support can still inspect recent history.
 */
@Component
public class TokenPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TokenPurgeJob.class);

    private final RefreshTokenRepository refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final Duration retention;

    public TokenPurgeJob(
            RefreshTokenRepository refreshTokens,
            EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository resetTokens,
            @Value("${TOKEN_PURGE_RETENTION_DAYS:7}") long retentionDays) {
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${TOKEN_PURGE_CRON:0 30 3 * * *}")
    @Transactional
    public void purge() {
        Instant cutoff = Instant.now().minus(retention);
        int refresh = refreshTokens.deleteStaleBefore(cutoff);
        int verification = verificationTokens.deleteExpiredBefore(cutoff);
        int reset = resetTokens.deleteExpiredBefore(cutoff);
        log.info("Token purge: removed {} refresh, {} email-verification, {} password-reset tokens older than {}",
                refresh, verification, reset, cutoff);
    }
}
