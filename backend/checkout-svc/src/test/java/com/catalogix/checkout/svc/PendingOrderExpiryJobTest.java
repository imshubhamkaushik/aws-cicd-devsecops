package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.OrderStatus;
import com.catalogix.checkout.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingOrderExpiryJobTest {

    private OrderRepository orders;
    private CheckoutSvc checkoutSvc;
    private PendingOrderExpiryJob job;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        checkoutSvc = mock(CheckoutSvc.class);
        job = new PendingOrderExpiryJob(orders, checkoutSvc, 30, 50);
    }

    @Test
    void expiresEveryStaleUnpaidOrder() {
        when(orders.findIdsByStatusCreatedBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        job.sweep();

        verify(checkoutSvc).expireUnpaidOrder(1L);
        verify(checkoutSvc).expireUnpaidOrder(2L);
    }

    @Test
    void oneFailingOrderDoesNotStopTheSweep() {
        when(orders.findIdsByStatusCreatedBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("inventory-svc down")).when(checkoutSvc).expireUnpaidOrder(1L);

        job.sweep();

        verify(checkoutSvc).expireUnpaidOrder(2L);
    }

    @Test
    void doesNothingWhenNoOrderIsStale() {
        when(orders.findIdsByStatusCreatedBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        job.sweep();

        verify(checkoutSvc, never()).expireUnpaidOrder(any());
    }

    @Test
    void returnsEveryStalePaymentClaimToPendingPayment() {
        when(orders.findIdsByStatusPaymentStartedBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(7L, 8L));

        job.sweep();

        verify(checkoutSvc).releaseStalePaymentClaim(eq(7L), any(Instant.class));
        verify(checkoutSvc).releaseStalePaymentClaim(eq(8L), any(Instant.class));
    }

    @Test
    void oneFailingClaimReleaseDoesNotStopTheSweepOrTheExpiry() {
        when(orders.findIdsByStatusPaymentStartedBefore(
                eq(OrderStatus.PAYMENT_PROCESSING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(7L, 8L));
        doThrow(new RuntimeException("db down")).when(checkoutSvc).releaseStalePaymentClaim(eq(7L), any(Instant.class));
        when(orders.findIdsByStatusCreatedBefore(eq(OrderStatus.PENDING_PAYMENT), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(1L));

        job.sweep();

        verify(checkoutSvc).releaseStalePaymentClaim(eq(8L), any(Instant.class));
        verify(checkoutSvc).expireUnpaidOrder(1L);
    }
}
