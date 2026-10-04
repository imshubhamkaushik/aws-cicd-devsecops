package com.catalogix.inventory.controller;

import com.catalogix.inventory.dto.AdjustInventoryRequest;
import com.catalogix.inventory.dto.InitInventoryRequest;
import com.catalogix.inventory.dto.InventoryResponse;
import com.catalogix.inventory.exception.ForbiddenException;
import com.catalogix.inventory.svc.InventorySvc;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal service — not exposed through the gateway. catalog-svc calls
 * GET/init (to compose product responses and to seed stock on product
 * creation); checkout-svc calls adjust() directly on the fast path (reserve
 * at order time), and again from its compensation outbox processor on the
 * retry path (release on failure).
 *
 * adjust() requires a SYSTEM-role token (catalog-svc and checkout-svc mint one per call), so
 * stock cannot be changed outside the checkout saga even by someone who reaches this port
 * directly. GET and init stay open to any authenticated caller: reading is harmless and
 * init only seeds a row for a product the caller just created.
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventorySvc svc;

    public InventoryController(InventorySvc svc) {
        this.svc = svc;
    }

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable Long productId) {
        return svc.get(productId);
    }

    @PostMapping
    public ResponseEntity<InventoryResponse> init(
            @Valid @RequestBody InitInventoryRequest req,
            @RequestAttribute("userRole") String role
    ) {
        if (!"SYSTEM".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Inventory initialization must be initiated by catalog-svc");
        }
        InventoryResponse resp = svc.init(req.getProductId(), req.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PatchMapping("/{productId}/adjust")
    public InventoryResponse adjust(
            @PathVariable Long productId,
            @RequestBody AdjustInventoryRequest req,
            @RequestAttribute("userRole") String role,
            // Optional idempotency headers sent by checkout-svc — see InventorySvc.adjust.
            @RequestHeader(value = "X-Operation-Id", required = false) String operationId,
            @RequestHeader(value = "X-Undo-Of", required = false) String undoOf
    ) {
        if (!"SYSTEM".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Stock adjustments must go through checkout-svc or catalog-svc, not be called directly");
        }
        if (operationId == null && undoOf == null) {
            return svc.adjust(productId, req.getDelta());
        }
        return svc.adjust(productId, req.getDelta(), operationId, undoOf);
    }
}
