package com.catalogix.inventory.repository;

import com.catalogix.inventory.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    // Row-locked read: two concurrent adjustments for the same product can't
    // both read a stale quantity and both succeed when only one should.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryItem i WHERE i.productId = :productId")
    Optional<InventoryItem> findByProductIdForUpdate(@Param("productId") Long productId);
}
