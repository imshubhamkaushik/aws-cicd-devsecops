package com.catalogix.checkout.svc;

import com.catalogix.checkout.client.AddressClient;
import com.catalogix.checkout.client.CartClient;
import com.catalogix.checkout.client.CatalogClient;
import com.catalogix.checkout.client.CheckoutClients;
import com.catalogix.checkout.client.PaymentClient;
import com.catalogix.checkout.dto.CreateOrderRequest;
import com.catalogix.checkout.dto.InvoiceLineResponse;
import com.catalogix.checkout.dto.InvoiceResponse;
import com.catalogix.checkout.dto.OrderItemResponse;
import com.catalogix.checkout.dto.OrderResponse;
import com.catalogix.checkout.dto.OrderTrackingResponse;
import com.catalogix.checkout.dto.PagedResponse;
import com.catalogix.checkout.dto.PayOrderRequest;
import com.catalogix.checkout.dto.ShippingAddressSummary;
import com.catalogix.checkout.dto.TrackingEventResponse;
import com.catalogix.checkout.event.OrderCancelledEvent;
import com.catalogix.checkout.event.OrderConfirmedEvent;
import com.catalogix.checkout.event.OrderItemEventData;
import com.catalogix.checkout.exception.ForbiddenException;
import com.catalogix.checkout.exception.InvalidOrderStateException;
import com.catalogix.checkout.exception.OrderNotFoundException;
import com.catalogix.checkout.exception.RefundFailedException;
import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.Order;
import com.catalogix.checkout.model.OrderItem;
import com.catalogix.checkout.model.OrderStatus;
import com.catalogix.checkout.model.OrderStatusEvent;
import com.catalogix.checkout.model.PaymentMethod;
import com.catalogix.checkout.repository.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates the order saga: place, pay, ship, deliver and cancel. Cart, payment, catalog and
 * inventory are separate services reached over HTTP, so there is no shared transaction;
 * failures are undone by compensating calls instead.
 *
 * Placement reserves stock per item (inventory-svc), then persists the order locally. If any step
 * fails, the reservations already made are released. A release that fails live is queued in
 * compensation_outbox and retried by CompensationOutboxProcessor.
 *
 * Placement and payment are deliberately not @Transactional as a whole. They make several HTTP
 * calls (up to ~11s each), and a method-wide transaction would hold a pooled JDBC connection
 * (and, for payment, a row lock) across all of them, exhausting the pool under a few slow
 * checkouts. Each DB step runs in its own short transaction via TransactionOperations, and remote
 * calls happen between them with no connection held. cancelOrder and expireUnpaidOrder do call
 * other services inside a transaction, so their compensation is written through the outbox.
 */
@Service
public class CheckoutSvc {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSvc.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(OrderStatus.CONFIRMED,
            Set.of(OrderStatus.SHIPPED), OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED));

    private final OrderRepository repo;
    private final CompensationOutboxWriter outboxWriter;
    private final CheckoutClients clients;
    private final ApplicationEventPublisher eventPublisher;
    // Runs a DB step in its own short transaction (in production a TransactionTemplate).
    private final TransactionOperations tx;

    /**
     * Convenience constructor for unit tests: every "transaction" is just a direct call, so
     * mock-based tests exercise the same code path without a database.
     */
    public CheckoutSvc(OrderRepository repo, CompensationOutboxWriter outboxWriter, CheckoutClients clients,
            ApplicationEventPublisher eventPublisher) {
        this(repo, outboxWriter, clients, eventPublisher, TransactionOperations.withoutTransaction());
    }

    @Autowired
    public CheckoutSvc(OrderRepository repo, CompensationOutboxWriter outboxWriter, CheckoutClients clients,
            ApplicationEventPublisher eventPublisher, TransactionOperations tx) {
        this.repo = repo;
        this.outboxWriter = outboxWriter;
        this.clients = clients;
        this.eventPublisher = eventPublisher;
        this.tx = tx;
    }

    // reserveOperationId is the idempotency id used to reserve this line, or null when the
    // reservation belongs to an already-persisted order.
    private record ReservedItem(Long productId, Long sellerId, String productName, BigDecimal price, int quantity,
            String reserveOperationId) {
    }

    public record OrderCreationResult(OrderResponse order, boolean wasNew) {
    }

    public record OrderPaymentResult(OrderResponse order, boolean paymentSucceeded) {
    }

    /** Places an order from explicitly supplied items; replays the existing order for a repeated idempotency key. */
    public OrderCreationResult createOrder(Long userId, CreateOrderRequest req, String bearerToken,
            String idempotencyKey) {
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<OrderCreationResult> existing = existingOrder(userId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<CartClient.ItemLine> lines = req.getItems().stream()
                .map(i -> new CartClient.ItemLine(i.getProductId(), i.getQuantity())).toList();

        return placeOrder(userId, lines, req.getAddressId(), bearerToken, idempotencyKey);
    }

    /** Places an order from the caller's cart in cart-svc, then clears the cart. */
    public OrderCreationResult checkoutFromCart(Long userId, Long addressId, String bearerToken,
            String idempotencyKey) {
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<OrderCreationResult> existing = existingOrder(userId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        CartClient.Handoff handoff = clients.cart().handoff(bearerToken);
        if (handoff == null) {
            throw new IllegalStateException("cart-svc returned a null checkout handoff");
        }

        OrderCreationResult result = placeOrder(userId, handoff.items(), addressId, bearerToken,
                idempotencyKey);

        if (result.wasNew()) {
            try {
                clients.cart().clear(bearerToken);
            } catch (RuntimeException e) {
                log.warn("Order {} placed but clearing the cart failed: {}", result.order().getId(), e.getMessage());
            }
        }
        return result;
    }

    private Optional<OrderCreationResult> existingOrder(Long userId, String idempotencyKey) {
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        final String key = idempotencyKey;
        // Mapped to a DTO inside the transaction so no lazy state escapes it.
        return tx.execute(status -> repo.findByUserIdAndIdempotencyKey(userId, key)
                .map(order -> new OrderCreationResult(toResponse(order), false)));
    }

    private OrderCreationResult placeOrder(Long userId, List<CartClient.ItemLine> items,
            Long addressId, String bearerToken, String idempotencyKey) {

        List<ReservedItem> reserved = new ArrayList<>();
        String reservationId = sagaId(userId, idempotencyKey);

        try {
            BigDecimal subtotal = BigDecimal.ZERO;
            int lineIndex = 0;
            for (CartClient.ItemLine line : items) {
                CatalogClient.ProductDto product = clients.catalog().fetch(line.productId(), bearerToken);
                String reserveOp = reservationId + ":reserve:" + lineIndex++;
                ReservedItem item = new ReservedItem(product.id(), product.ownerId(), product.name(), product.price(),
                        line.quantity(), reserveOp);
                reserved.add(item);
                clients.inventory().adjust(line.productId(), -line.quantity(), reserveOp, null);
                subtotal = subtotal.add(product.price().multiply(BigDecimal.valueOf(line.quantity())));
            }

            Order order = new Order();
            order.setUserId(userId);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setIdempotencyKey(idempotencyKey);
            for (ReservedItem r : reserved) {
                OrderItem orderItem = new OrderItem(r.productId(), r.productName(), r.quantity(), r.price());
                orderItem.setSellerId(r.sellerId());
                order.addItem(orderItem);
            }
            order.setTotalAmount(subtotal);

            if (addressId != null) {
                AddressClient.AddressDto address = clients.address().fetch(addressId, bearerToken);
                order.setShippingLabel(address.label());
                order.setShippingLine1(address.line1());
                order.setShippingLine2(address.line2());
                order.setShippingCity(address.city());
                order.setShippingState(address.state());
                order.setShippingPincode(address.pincode());
                order.setShippingPhone(address.phone());
            }
            order.addStatusEvent(OrderStatus.PENDING_PAYMENT, "Order placed");

            return tx.execute(status -> new OrderCreationResult(toResponse(repo.save(order)), true));
        } catch (RuntimeException failure) {
            if (idempotencyKey != null && failure instanceof DataIntegrityViolationException
                    && repo.findByUserIdAndIdempotencyKey(userId, idempotencyKey).isPresent()) {
                log.info("Concurrent request with the same Idempotency-Key already created the order; "
                        + "not releasing the shared reservation");
                throw failure;
            }
            compensate(reserved, "attempt-" + reservationId);
            throw failure;
        }
    }

    /** Pays without an idempotency key; prefer the keyed overload so a timed-out payment can be replayed safely. */
    public OrderPaymentResult payOrder(Long orderId, Long userId, String role, PayOrderRequest req,
            String userEmail) {
        return payOrderInternal(orderId, userId, role, req, userEmail, null);
    }

    public OrderPaymentResult payOrder(Long orderId, Long userId, String role, PayOrderRequest req,
            String userEmail, String idempotencyKey) {
        return payOrderInternal(orderId, userId, role, req, userEmail, idempotencyKey);
    }

    /**
     * Pays for an order in three steps, so that no DB connection or row lock is held while
     * payment-svc is being called (up to ~11s):
     *
     * 1. claim   (short tx, row-locked): PENDING_PAYMENT -> PAYMENT_PROCESSING. This is the
     *            mutual exclusion: payment-svc only dedupes by Idempotency-Key, so two requests
     *            with different keys must not both reach it. The second one is rejected.
     * 2. charge  (no tx): call payment-svc. If the call throws, the outcome is unknown; the
     *            claim is released back to PENDING_PAYMENT so the customer can retry, and the
     *            browser's stable Idempotency-Key makes payment-svc replay the first result
     *            instead of charging again.
     * 3. settle  (short tx, row-locked): CONFIRMED on success, CANCELLED (+ stock
     *            release) on decline.
     *
     * A pod that dies between 1 and 3 strands the order in PAYMENT_PROCESSING;
     * PendingOrderExpiryJob returns such orders to PENDING_PAYMENT.
     */
    private OrderPaymentResult payOrderInternal(Long orderId, Long userId, String role, PayOrderRequest req,
            String userEmail, String idempotencyKey) {
        BigDecimal amount = claimForPayment(orderId, userId, role);

        PaymentClient.PaymentOutcome payment;
        try {
            payment = (idempotencyKey == null || idempotencyKey.isBlank())
                    ? clients.payment().process(orderId, userId, amount, req)
                    : clients.payment().process(orderId, userId, amount, req, idempotencyKey);
        } catch (RuntimeException unknownOutcome) {
            releasePaymentClaimQuietly(orderId);
            throw unknownOutcome;
        }

        return settlePayment(orderId, amount, req, userEmail, payment);
    }

    /** Step 1: atomically PENDING_PAYMENT -> PAYMENT_PROCESSING; returns the amount to charge. */
    private BigDecimal claimForPayment(Long orderId, Long userId, String role) {
        return tx.execute(status -> {
            Order order = repo.findByIdForUpdate(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

            assertCanAccess(order, userId, role);

            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new InvalidOrderStateException(
                        "Order " + orderId + " is not awaiting payment (current status: " + order.getStatus() + ")");
            }

            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            order.setPaymentStartedAt(Instant.now());
            repo.save(order);
            return order.getTotalAmount();
        });
    }

    /** Result of step 3; {@code order} is null when the order was no longer payable. */
    private record Settled(OrderResponse order, OrderStatus statusFound) {
    }

    /** Step 3: records the payment outcome, refunding a successful charge that arrived too late. */
    private OrderPaymentResult settlePayment(
            Long orderId,
            BigDecimal amount,
            PayOrderRequest req,
            String userEmail,
            PaymentClient.PaymentOutcome payment) {

        Settled settled = tx.execute(status -> settlePaymentInTransaction(orderId, req, userEmail, payment));

        if (settled.order() == null) {
            boolean refunded = refundLatePaymentIfRequired(orderId, amount, payment, settled);

            throw buildLatePaymentException(orderId, payment, settled.statusFound(), refunded);
        }

        return new OrderPaymentResult(settled.order(), payment.succeeded());
    }

    /**
     * Persists the payment result in a short transaction. PENDING_PAYMENT is accepted because the
     * stale-claim sweep may have released the claim while payment-svc was still processing.
     */
    private Settled settlePaymentInTransaction(
            Long orderId,
            PayOrderRequest req,
            String userEmail,
            PaymentClient.PaymentOutcome payment) {

        Order order = txLockedOrder(orderId);

        if (!isPaymentSettleable(order)) {
            return new Settled(null, order.getStatus());
        }

        applyPaymentOutcome(order, req, payment, userEmail);

        Order saved = repo.save(order);

        publishConfirmationEventIfNeeded(saved, userEmail, payment);

        return new Settled(toResponse(saved), saved.getStatus());
    }

    private Order txLockedOrder(Long orderId) {
        return repo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private boolean isPaymentSettleable(Order order) {
        return order.getStatus() == OrderStatus.PAYMENT_PROCESSING
                || order.getStatus() == OrderStatus.PENDING_PAYMENT;
    }

    private void applyPaymentOutcome(
            Order order,
            PayOrderRequest req,
            PaymentClient.PaymentOutcome payment,
            String userEmail) {

        if (payment.succeeded()) {
            applySuccessfulPayment(order, req, payment, userEmail);
            return;
        }

        applyDeclinedPayment(order);
    }

    private void applySuccessfulPayment(
            Order order,
            PayOrderRequest req,
            PaymentClient.PaymentOutcome payment,
            String userEmail) {

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(req.getMethod());
        order.setPaymentReference(payment.reference());
        order.setCustomerEmail(userEmail);

        String note = successfulPaymentNote(payment);
        order.addStatusEvent(OrderStatus.CONFIRMED, note);
    }

    private String successfulPaymentNote(PaymentClient.PaymentOutcome payment) {
        if ("COD_PENDING".equals(payment.status())) {
            return "Order confirmed — pay on delivery";
        }

        return "Payment confirmed";
    }

    private void applyDeclinedPayment(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        order.addStatusEvent(OrderStatus.CANCELLED, "Payment declined");

        // Persist the terminal local state before touching remote inventory.
        repo.save(order);
        repo.flush();

        releaseOrderSideEffects(order, "payment-failed-order-" + order.getId());
    }

    private void publishConfirmationEventIfNeeded(
            Order saved,
            String userEmail,
            PaymentClient.PaymentOutcome payment) {

        if (payment.succeeded()) {
            eventPublisher.publishEvent(
                    new OrderConfirmedEvent(
                            saved.getId(),
                            saved.getUserId(),
                            userEmail,
                            toEventItems(saved),
                            saved.getTotalAmount()));
        }
    }

    private boolean refundLatePaymentIfRequired(
            Long orderId,
            BigDecimal amount,
            PaymentClient.PaymentOutcome payment,
            Settled settled) {

        if (!payment.succeeded() || "COD_PENDING".equals(payment.status())) {
            return false;
        }

        try {
            clients.refund().refund(orderId, amount, "late-payment-" + orderId);
            return true;
        } catch (RuntimeException e) {
            log.error(
                    "Order {} was {} when its payment completed and the automatic refund FAILED"
                            + " — refund manually: {}",
                    orderId,
                    settled.statusFound(),
                    e.getMessage());

            return false;
        }
    }

    private InvalidOrderStateException buildLatePaymentException(
            Long orderId,
            PaymentClient.PaymentOutcome payment,
            OrderStatus statusFound,
            boolean refunded) {

        String message = "Order " + orderId
                + " was " + statusFound
                + " while its payment was being processed";

        if (payment.succeeded()) {
            message += latePaymentRefundMessage(refunded);
        }

        return new InvalidOrderStateException(message);
    }

    private String latePaymentRefundMessage(boolean refunded) {
        if (refunded) {
            return "; the payment has been refunded";
        }

        return "; the payment could not be refunded automatically and will be reviewed";
    }

    /** Unknown payment outcome: give the order back so the customer (or the sweep) can retry. */
    private void releasePaymentClaimQuietly(Long orderId) {
        try {
            tx.executeWithoutResult(status -> repo.findByIdForUpdate(orderId)
                    .filter(o -> o.getStatus() == OrderStatus.PAYMENT_PROCESSING).ifPresent(o -> {
                        o.setStatus(OrderStatus.PENDING_PAYMENT);
                        repo.save(o);
                    }));
        } catch (RuntimeException e) {
            // Not fatal: PendingOrderExpiryJob releases stale claims.
            log.warn("Could not release the payment claim on order {}: {}", orderId, e.getMessage());
        }
    }

    /**
     * Returns an order stranded in PAYMENT_PROCESSING to PENDING_PAYMENT. A retry is safe because
     * the stable Idempotency-Key makes payment-svc replay the first result instead of charging
     * again. Called by PendingOrderExpiryJob.
     *
     * @return true if this call released the claim
     */
    @Transactional
    public boolean releaseStalePaymentClaim(Long orderId, Instant claimedBefore) {
        Order order = repo.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            return false;
        }
        Instant claimedAt = order.getPaymentStartedAt();
        if (claimedAt != null && !claimedAt.isBefore(claimedBefore)) {
            return false; // claimed again since the sweep listed it
        }
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        repo.save(order);
        log.warn("Order {} was stuck in PAYMENT_PROCESSING since {}; returned to PENDING_PAYMENT. If the customer"
                + " reports a charge without a confirmed order, reconcile with payment-svc.", orderId, claimedAt);
        return true;
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = repo.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Set<OrderStatus> allowed = ALLOWED_STATUS_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(newStatus)) {
            throw new InvalidOrderStateException(
                    "Cannot move order " + orderId + " from " + order.getStatus() + " to " + newStatus);
        }
        order.setStatus(newStatus);
        order.addStatusEvent(newStatus, switch (newStatus) {
        case SHIPPED -> "Order shipped";
        case DELIVERED -> "Order delivered";
        default -> "Status updated to " + newStatus;
        });
        return toResponse(repo.save(order));
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findExistingByIdempotencyKey(Long userId, String idempotencyKey) {
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (idempotencyKey == null)
            return Optional.empty();
        return repo.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> listOrders(Long userId, String role, Pageable pageable) {
        Page<Order> page = "ADMIN".equalsIgnoreCase(role) ? repo.findAll(pageable)
                : repo.findByUserId(userId, pageable);
        return PagedResponse.from(page, page.getContent().stream().map(this::toResponse).toList());
    }


    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id, Long userId, String role) {
        Order order = repo.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        assertCanAccess(order, userId, role);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderTrackingResponse getTracking(Long id, Long userId, String role) {
        Order order = repo.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        assertCanAccess(order, userId, role);
        List<TrackingEventResponse> events = order.getStatusEvents().stream()
                .sorted(java.util.Comparator.comparing(OrderStatusEvent::getCreatedAt))
                .map(e -> new TrackingEventResponse(e.getStatus(), e.getNote(), e.getCreatedAt())).toList();
        return new OrderTrackingResponse(order.getId(), order.getStatus(), events);
    }

    // Fixed placeholder seller details and a flat 18% GST rate; there is no seller registry or tax engine.
    private static final String SELLER_NAME = "Catalogix Retail Pvt. Ltd.";
    private static final String SELLER_ADDRESS = "3rd Floor, Tech Park One, Chandigarh, Punjab 160101, India";
    private static final BigDecimal TAX_RATE_PERCENT = new BigDecimal("18.00");

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long id, Long userId, String role) {
        Order order = repo.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        assertCanAccess(order, userId, role);

        if (order.getPaymentMethod() == null) {
            throw new InvalidOrderStateException("No invoice available for order " + id + " — it was never paid for");
        }

        List<InvoiceLineResponse> lines = order.getItems().stream().map(
                i -> new InvoiceLineResponse(i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();
        BigDecimal itemsSubtotal = lines.stream().map(InvoiceLineResponse::subtotal).reduce(BigDecimal.ZERO,
                BigDecimal::add);

        // The captured total is tax-inclusive; tax is derived from it so taxableValue + taxAmount
        // always equals the amount charged, with no rounding gap.
        BigDecimal totalAmount = order.getTotalAmount();
        BigDecimal taxDivisor = BigDecimal.ONE.add(TAX_RATE_PERCENT.divide(new BigDecimal("100")));
        BigDecimal taxableValue = totalAmount.divide(taxDivisor, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal taxAmount = totalAmount.subtract(taxableValue);

        InvoiceResponse invoice = new InvoiceResponse();
        invoice.setInvoiceNumber("INV-" + String.format("%08d", order.getId()));
        invoice.setOrderId(order.getId());
        invoice.setOrderDate(order.getCreatedAt());
        invoice.setSellerName(SELLER_NAME);
        invoice.setSellerAddress(SELLER_ADDRESS);
        invoice.setCustomerEmail(order.getCustomerEmail());
        invoice.setBillingAddress(order.getShippingLine1() == null ? null
                : new ShippingAddressSummary(order.getShippingLabel(), order.getShippingLine1(),
                        order.getShippingLine2(), order.getShippingCity(), order.getShippingState(),
                        order.getShippingPincode(), order.getShippingPhone()));
        invoice.setItems(lines);
        invoice.setItemsSubtotal(itemsSubtotal);
        invoice.setTaxableValue(taxableValue);
        invoice.setTaxRatePercent(TAX_RATE_PERCENT);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(totalAmount);
        invoice.setPaymentMethod(order.getPaymentMethod());
        invoice.setPaymentReference(order.getPaymentReference());
        return invoice;
    }

    @Transactional
    public OrderResponse cancelOrder(Long id, Long userId, String role, String userEmail) {
        // Row-locked so a cancel racing a payment (or another cancel) waits instead of acting on stale status.
        Order order = repo.findByIdForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
        assertCanAccess(order, userId, role);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        if (order.getStatus() == OrderStatus.PAYMENT_PROCESSING) {
            throw new InvalidOrderStateException(
                    "A payment for order " + id + " is being processed right now; try cancelling again in a moment");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    "Order " + id + " can no longer be cancelled (current status: " + order.getStatus() + ")");
        }

        if (order.getStatus() == OrderStatus.CONFIRMED && order.getPaymentMethod() != null
                && order.getPaymentMethod() != PaymentMethod.COD) {
            try {
                clients.refund().refund(order.getId(), order.getTotalAmount(), "cancel-order-" + order.getId());
            } catch (RuntimeException e) {
                throw new RefundFailedException("Refund failed for cancelled order " + id);
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        boolean isOwnCancellation = order.getUserId() != null && order.getUserId().equals(userId);
        order.addStatusEvent(OrderStatus.CANCELLED, isOwnCancellation ? "Cancelled by customer" : "Cancelled by admin");
        // Flush the local transition first so a remote release is never done without the cancellation saved.
        repo.save(order);
        repo.flush();
        releaseOrderSideEffects(order, "cancel-order-" + id);

        Order saved = repo.save(order);
        eventPublisher.publishEvent(
                new OrderCancelledEvent(saved.getId(), saved.getUserId(), userEmail, toEventItems(saved)
            )
        );
        return toResponse(saved);
    }

    /**
     * Cancels an order that was placed but never paid and releases its reserved stock. Called by
     * PendingOrderExpiryJob. Row-locked and status-checked, so it is a no-op if the customer paid
     * or cancelled in the meantime.
     *
     * @return true if the order was expired by this call
     */
    @Transactional
    public boolean expireUnpaidOrder(Long orderId) {
        Order order = repo.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.addStatusEvent(OrderStatus.CANCELLED, "Cancelled automatically: payment was not received in time");
        repo.save(order);
        repo.flush();
        releaseOrderSideEffects(order, "expire-unpaid-order-" + orderId);
        return true;
    }

    private void releaseOrderSideEffects(Order order, String outboxReason) {
        List<ReservedItem> asReserved = order.getItems().stream().map(i -> new ReservedItem(i.getProductId(),
                i.getSellerId(), i.getProductName(), i.getUnitPrice(), i.getQuantity(), null)).toList();
        compensateWithReason(asReserved, outboxReason, "order-" + order.getId(), false);
    }

    private void compensate(List<ReservedItem> reserved, String scope) {
        compensateWithReason(reserved, "compensate-failed-order-creation", scope, true);
    }

    // scope makes each release's idempotency id unique to one logical release, so retrying it,
    // live or later from the outbox, releases each line at most once.
    private void compensateWithReason(List<ReservedItem> reserved,
            String reason, String scope, boolean independentOutbox) {
        int lineIndex = 0;
        for (ReservedItem r : reserved) {
            String releaseOp = "release:" + scope + ":" + lineIndex++;
            try {
                clients.inventory().adjust(r.productId(), r.quantity(), releaseOp, r.reserveOperationId());
            } catch (RuntimeException compensationError) {
                log.warn("Live stock-release failed for product {} ({}), queuing to outbox: {}",
                        r.productId(), reason, compensationError.getMessage());
                CompensationOutbox entry = CompensationOutbox.releaseStock(
                        r.productId(), r.quantity(), reason, releaseOp, r.reserveOperationId());
                persistCompensation(entry, independentOutbox);
            }
        }
    }

    private void persistCompensation(CompensationOutbox entry, boolean independent) {
        if (independent) {
            outboxWriter.enqueueIndependent(entry);
        } else {
            outboxWriter.enqueue(entry);
        }
    }

    /**
     * Trims the idempotency key and enforces the orders.idempotency_key VARCHAR(64) limit.
     * Blank keys are treated as absent.
     */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return normalized;
    }

    /**
     * Derives a saga id that is stable across retries when an Idempotency-Key is supplied, so a
     * retried checkout reuses its reservation ids instead of reserving stock twice. Without a key
     * the id is random.
     */
    private String sagaId(Long userId, String idempotencyKey) {
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        if (idempotencyKey == null) {
            return java.util.UUID.randomUUID().toString();
        }
        String input = userId + ":" + idempotencyKey;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "key-" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for checkout idempotency ids", e);
        }
    }

    private void assertCanAccess(Order order, Long userId, String role) {
        boolean isOwner = order.getUserId() != null && order.getUserId().equals(userId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("You may only view or manage your own orders");
        }
    }

    private List<OrderItemEventData> toEventItems(Order order) {
        return order.getItems().stream().map(i -> new OrderItemEventData(i.getProductId(), i.getSellerId(),
                i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal())).toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream().map(i -> {
            OrderItemResponse response = new OrderItemResponse(i.getProductId(), i.getProductName(), i.getQuantity(),
                    i.getUnitPrice(), i.getSubtotal());
            response.setSellerId(i.getSellerId());
            return response;
        }).toList();
        ShippingAddressSummary shippingAddress = order.getShippingLine1() == null ? null
                : new ShippingAddressSummary(order.getShippingLabel(), order.getShippingLine1(),
                        order.getShippingLine2(), order.getShippingCity(), order.getShippingState(),
                        order.getShippingPincode(), order.getShippingPhone());
        OrderResponse response = new OrderResponse(order.getId(), order.getUserId(), order.getStatus(),
                order.getTotalAmount(), order.getCreatedAt(), items);
        response.setShippingAddress(shippingAddress);
        return response;
    }
}