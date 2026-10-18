package com.catalogix.payment.repository;

import com.catalogix.payment.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByOrderId(Long orderId);

    java.util.Optional<Refund> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);
}
