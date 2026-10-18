package com.catalogix.checkout.controller;

import com.catalogix.checkout.dto.CheckoutFromCartRequest;
import com.catalogix.checkout.dto.CreateOrderRequest;
import com.catalogix.checkout.dto.InvoiceResponse;
import com.catalogix.checkout.dto.OrderResponse;
import com.catalogix.checkout.dto.OrderTrackingResponse;
import com.catalogix.checkout.dto.PagedResponse;
import com.catalogix.checkout.dto.PayOrderRequest;
import com.catalogix.checkout.dto.UpdateOrderStatusRequest;
import com.catalogix.checkout.exception.ForbiddenException;
import com.catalogix.checkout.svc.CheckoutSvc;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.function.Supplier;

// Caller identity (userId/userRole) and the bearer token come from JwtAuthFilter's request
// attributes; service-to-service mutations use SYSTEM tokens minted by their clients.

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CheckoutSvc svc;

    public OrderController(CheckoutSvc svc) {
        this.svc = svc;
    }

    // Direct API with an explicit item list. A repeated Idempotency-Key returns the original
    // order with 200 instead of creating a duplicate.
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("bearerToken") String bearerToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest req
    ) {
        CheckoutSvc.OrderCreationResult result = withIdempotencyRaceRecovery(
                userId, idempotencyKey,
                () -> svc.createOrder(userId, req, bearerToken, idempotencyKey));
        return respond(result);
    }

    // Cart-driven checkout with the same idempotency-key race recovery as create(). The body is
    // optional; without an addressId the order has no shipping snapshot.
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("bearerToken") String bearerToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CheckoutFromCartRequest req
    ) {
        Long addressId = req != null ? req.getAddressId() : null;
        CheckoutSvc.OrderCreationResult result = withIdempotencyRaceRecovery(
                userId, idempotencyKey,
                () -> svc.checkoutFromCart(userId, addressId, bearerToken, idempotencyKey));
        return respond(result);
    }

    private CheckoutSvc.OrderCreationResult withIdempotencyRaceRecovery(
            Long userId, String idempotencyKey, Supplier<CheckoutSvc.OrderCreationResult> action
    ) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException e) {
            // Lost a race on the same Idempotency-Key; return the winner's order.
            OrderResponse existing = svc.findExistingByIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> e);
            return new CheckoutSvc.OrderCreationResult(existing, false);
        }
    }

    private ResponseEntity<OrderResponse> respond(CheckoutSvc.OrderCreationResult result) {
        if (!result.wasNew()) {
            return ResponseEntity.ok(result.order());
        }
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/api/orders/{id}")
                .buildAndExpand(result.order().getId())
                .toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderResponse>> list(
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok(svc.listOrders(userId, role, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOne(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role
    ) {
        return ResponseEntity.ok(svc.getOrder(id, userId, role));
    }

    // Status history for the order; same ownership rule as getOne.
    @GetMapping("/{id}/tracking")
    public ResponseEntity<OrderTrackingResponse> getTracking(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role
    ) {
        return ResponseEntity.ok(svc.getTracking(id, userId, role));
    }

    // Structured invoice data; rendering is left to the frontend. Same ownership rule as getOne.
    @GetMapping("/{id}/invoice")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role
    ) {
        return ResponseEntity.ok(svc.getInvoice(id, userId, role));
    }


    @PostMapping("/{id}/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role,
            @RequestAttribute("userEmail") String userEmail,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayOrderRequest req
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for payment requests");
        }
        CheckoutSvc.OrderPaymentResult result =
                svc.payOrder(id, userId, role, req, userEmail, idempotencyKey.trim());
        // Rebuilds the {order, payment: {status}} shape the frontend expects.
        Map<String, Object> payment = Map.of("status", result.paymentSucceeded() ? "SUCCEEDED" : "FAILED");
        return ResponseEntity.ok(Map.of("order", result.order(), "payment", payment));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestAttribute("userRole") String role,
            @Valid @RequestBody UpdateOrderStatusRequest req
    ) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Only admins may update order status");
        }
        return ResponseEntity.ok(svc.updateStatus(id, req.getStatus()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute("userRole") String role,
            @RequestAttribute("userEmail") String userEmail
    ) {
        return ResponseEntity.ok(svc.cancelOrder(id, userId, role, userEmail));
    }
}
