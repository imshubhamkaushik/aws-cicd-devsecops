package com.catalogix.inventory.repository;

import com.catalogix.inventory.model.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface InventoryOperationRepository extends JpaRepository<InventoryOperation, String> {

    @Modifying
    @Query("DELETE FROM InventoryOperation o WHERE o.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
