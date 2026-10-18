package com.catalogix.user.repository;

import com.catalogix.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    long countByRole(String role);

    /**
     * Locks the current rows for a role so invariants based on the number of
     * privileged users (for example, keeping at least one ADMIN) cannot race
     * between concurrent role changes. PostgreSQL applies FOR UPDATE to the
     * selected rows; a second transaction waits and then re-evaluates the count
     * against the committed state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where upper(u.role) = upper(:role)")
    java.util.List<User> findAllByRoleForUpdate(@Param("role") String role);
}
