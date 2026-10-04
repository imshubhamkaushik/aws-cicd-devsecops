package com.catalogix.payment.repository;

import com.catalogix.payment.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    // Row-locked read of one payment. Refunds against it are serialised on this lock, so two
    // concurrent refunds cannot both see "nothing refunded yet" and together exceed the payment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByRequestedByUserIdAndIdempotencyKey(Long requestedByUserId, String idempotencyKey);
}
