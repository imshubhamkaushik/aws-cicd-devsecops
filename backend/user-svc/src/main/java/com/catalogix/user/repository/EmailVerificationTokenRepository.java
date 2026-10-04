package com.catalogix.user.repository;

import com.catalogix.user.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
