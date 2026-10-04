package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.OrderStatus;
import com.catalogix.checkout.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Releases reserved stock held by orders that were placed but never paid.
 * Orders still in PENDING_PAYMENT after ORDER_PAYMENT_TIMEOUT_MINUTES (default 30) are cancelled
 * and their side effects released. Every replica may run the sweep: expireUnpaidOrder row-locks
 * and re-checks each order, so two replicas cannot expire the same one twice.
 *
 * The sweep also returns orders stranded in PAYMENT_PROCESSING (the paying pod died) to
 * PENDING_PAYMENT after PAYMENT_CLAIM_TIMEOUT_MINUTES (default 5, well above payment-svc's ~11s
 * worst case) so the customer can retry.
 */
@Component
public class PendingOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpiryJob.class);

    private final OrderRepository orders;
    private final CheckoutSvc checkoutSvc;
    private final Duration paymentWindow;
    private final Duration claimTimeout;
    private final int batchSize;

    @Autowired
    public PendingOrderExpiryJob(
            OrderRepository orders,
            CheckoutSvc checkoutSvc,
            @Value("${ORDER_PAYMENT_TIMEOUT_MINUTES:30}") long timeoutMinutes,
            @Value("${PENDING_ORDER_SWEEP_BATCH:50}") int batchSize,
            @Value("${PAYMENT_CLAIM_TIMEOUT_MINUTES:5}") long claimTimeoutMinutes) {
        this.orders = orders;
        this.checkoutSvc = checkoutSvc;
        this.paymentWindow = Duration.ofMinutes(timeoutMinutes);
        this.batchSize = batchSize;
        this.claimTimeout = Duration.ofMinutes(claimTimeoutMinutes);
    }

    /** Defaults the stale-claim timeout to 5 minutes. */
    public PendingOrderExpiryJob(OrderRepository orders, CheckoutSvc checkoutSvc, long timeoutMinutes,
            int batchSize) {
        this(orders, checkoutSvc, timeoutMinutes, batchSize, 5);
    }

    @Scheduled(fixedDelayString = "${PENDING_ORDER_SWEEP_MS:60000}")
    public void sweep() {
        releaseStalePaymentClaims();
        expireUnpaidOrders();
    }

    private void releaseStalePaymentClaims() {
        Instant claimCutoff = Instant.now().minus(claimTimeout);
        List<Long> stuck = orders.findIdsByStatusPaymentStartedBefore(
                OrderStatus.PAYMENT_PROCESSING, claimCutoff, PageRequest.of(0, batchSize));
        for (Long id : stuck) {
            try {
                checkoutSvc.releaseStalePaymentClaim(id, claimCutoff);
            } catch (RuntimeException e) {
                log.warn("Could not release stale payment claim on order {}: {}", id, e.getMessage());
            }
        }
    }

    private void expireUnpaidOrders() {
        Instant cutoff = Instant.now().minus(paymentWindow);
        List<Long> stale = orders.findIdsByStatusCreatedBefore(
                OrderStatus.PENDING_PAYMENT, cutoff, PageRequest.of(0, batchSize));
        for (Long id : stale) {
            try {
                if (checkoutSvc.expireUnpaidOrder(id)) {
                    log.info("Expired unpaid order {} (no payment within {})", id, paymentWindow);
                }
            } catch (RuntimeException e) {
                // One bad order must not stop the sweep; it is retried on the next run.
                log.warn("Could not expire unpaid order {}: {}", id, e.getMessage());
            }
        }
    }
}
