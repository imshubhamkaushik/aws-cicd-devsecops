package com.catalogix.user.repository;

import com.catalogix.user.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
