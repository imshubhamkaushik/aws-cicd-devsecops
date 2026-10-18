package com.catalogix.user.repository;

import com.catalogix.user.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // For the session list. Expiry is checked in the service layer (isValid()) so there is
    // a single definition of "valid".
    List<RefreshToken> findByUserIdAndRevokedFalseOrderByLastUsedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    // Every session of the user EXCEPT the one identified by keepHash — "sign out my
    // other devices" (e.g. after a password change) without ending this one.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true "
            + "WHERE t.userId = :userId AND t.revoked = false AND t.tokenHash <> :keepHash")
    int revokeAllForUserExcept(@Param("userId") Long userId, @Param("keepHash") String keepHash);

    // Atomic single-use claim: one conditional UPDATE. When two requests present the same
    // token concurrently, only one flips revoked to true (returns 1); the other returns 0.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.id = :id AND t.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    // Housekeeping: delete anything that's long past useful (expired or
    // revoked a while ago), so the table doesn't grow unbounded forever.
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff OR (t.revoked = true AND t.createdAt < :cutoff)")
    int deleteStaleBefore(@Param("cutoff") Instant cutoff);
}
