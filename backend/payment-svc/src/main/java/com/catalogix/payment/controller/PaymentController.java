package com.catalogix.payment.controller;

import com.catalogix.payment.dto.PaymentResponse;
import com.catalogix.payment.dto.ProcessPaymentRequest;
import com.catalogix.payment.dto.ProcessRefundRequest;
import com.catalogix.payment.dto.RefundResponse;
import com.catalogix.payment.exception.DeclinedException;
import com.catalogix.payment.exception.ForbiddenException;
import com.catalogix.payment.model.PaymentStatus;
import com.catalogix.payment.svc.PaymentSvc;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

/**
 * Called synchronously and only by checkout-svc, on the request thread that
 * is finalizing an order or processing an authorized refund —
 * never by a browser directly (no gateway route is exposed for this service).
 *
 * Requires a SYSTEM-role token (checkout-svc mints one per call, see its PaymentClient),
 * so a logged-in user cannot record payments directly. Because a system token's subject
 * is a sentinel, requestedByUserId is read from the request body.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentSvc svc;

    public PaymentController(PaymentSvc svc) {
        this.svc = svc;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> process(
            @Valid @RequestBody ProcessPaymentRequest req,
            @RequestAttribute("userRole") String role,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (!"SYSTEM".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Payments must be initiated by checkout-svc, not called directly");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for payment requests");
        }

        try {
            PaymentSvc.ProcessResult result =
                    svc.process(req, req.getRequestedByUserId(), idempotencyKey);
            return ResponseEntity
                    .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(result.response());
        } catch (DataIntegrityViolationException race) {
            // Two concurrent requests with the same user/key can both miss
            // the pre-check. The unique DB index makes exactly one winner.
            // Return that winner rather than leaking a raw 500 to checkout-svc.
            PaymentResponse existing =
                    svc.findExistingByIdempotencyKey(req.getRequestedByUserId(), idempotencyKey)
                            .orElseThrow(() -> race);
            if (existing.getStatus() == PaymentStatus.FAILED) {
                throw new DeclinedException("Payment declined");
            }
            return ResponseEntity.status(HttpStatus.OK).body(existing);
        }
    }

    // Same SYSTEM-only lockdown as /payments above — checkout-svc is the
    // only legitimate caller, only when an admin approves a return (see
    // checkout-svc's cancellation/refund flow), and only for CARD/UPI orders (COD never
    // reaches this at all, since there's nothing to refund).
    @PostMapping("/refund")
    public ResponseEntity<RefundResponse> refund(
            @Valid @RequestBody ProcessRefundRequest req,
            @RequestAttribute("userRole") String role,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (!"SYSTEM".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Refunds must be initiated by checkout-svc, not called directly");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for refund requests");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.refund(req, idempotencyKey.trim()));
    }
}
