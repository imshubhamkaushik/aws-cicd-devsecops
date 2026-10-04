package com.catalogix.checkout.repository;

import com.catalogix.checkout.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    // SELECT ... FOR UPDATE for short read-then-write transactions only. payOrder uses it to claim
    // PENDING_PAYMENT -> PAYMENT_PROCESSING and to settle the result; it is not held during the
    // payment-svc call. Concurrent pay requests serialise on it and the loser is rejected.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // Oldest unpaid orders first, for PendingOrderExpiryJob. The payment window restarts with each
    // attempt (payment_started_at), not only from creation time.
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.createdAt < :cutoff "
            + "AND (o.paymentStartedAt IS NULL OR o.paymentStartedAt < :cutoff) ORDER BY o.createdAt")
    java.util.List<Long> findIdsByStatusCreatedBefore(
            @Param("status") com.catalogix.checkout.model.OrderStatus status,
            @Param("cutoff") java.time.Instant cutoff,
            org.springframework.data.domain.Pageable pageable);

    // Orders stuck in PAYMENT_PROCESSING long enough that the claiming request must have died.
    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.paymentStartedAt < :cutoff "
            + "ORDER BY o.paymentStartedAt")
    java.util.List<Long> findIdsByStatusPaymentStartedBefore(
            @Param("status") com.catalogix.checkout.model.OrderStatus status,
            @Param("cutoff") java.time.Instant cutoff,
            org.springframework.data.domain.Pageable pageable);

}
