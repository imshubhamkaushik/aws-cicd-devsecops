package com.catalogix.user.repository;

import com.catalogix.user.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    // Ownership-scoped lookup — every read/update/delete goes through this
    // rather than plain findById, so one user can never touch another
    // user's address just by guessing an id.
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
