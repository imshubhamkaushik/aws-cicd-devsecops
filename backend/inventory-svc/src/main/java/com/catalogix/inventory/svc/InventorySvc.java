package com.catalogix.inventory.svc;

import com.catalogix.inventory.dto.InventoryResponse;
import com.catalogix.inventory.exception.InsufficientInventoryException;
import com.catalogix.inventory.exception.InventoryItemNotFoundException;
import com.catalogix.inventory.exception.IdempotencyConflictException;
import com.catalogix.inventory.model.InventoryItem;
import com.catalogix.inventory.model.InventoryOperation;
import com.catalogix.inventory.repository.InventoryItemRepository;
import com.catalogix.inventory.repository.InventoryOperationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventorySvc {

    private final InventoryItemRepository repo;
    private final InventoryOperationRepository operations;

    /** Without an idempotency ledger: operation ids are then ignored. */
    public InventorySvc(InventoryItemRepository repo) {
        this(repo, null);
    }

    @Autowired
    public InventorySvc(
            InventoryItemRepository repo,
            InventoryOperationRepository operations
    ) {
        this.operations = operations;
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long productId) {
        InventoryItem item = repo.findById(productId)
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));

        return new InventoryResponse(
                item.getProductId(),
                item.getQuantity()
        );
    }

    @Transactional
    public InventoryResponse init(Long productId, int initialQuantity) {
        // Idempotent: catalog-svc may retry product creation's follow-up
        // call after a timeout without knowing whether it landed.
        //
        // Read the existing row directly instead of calling the transactional
        // get() method through this instance. This also avoids the extra
        // repository round-trip that existsById() + get() would require.
        java.util.Optional<InventoryItem> existing = repo.findById(productId);

        if (existing.isPresent()) {
            InventoryItem item = existing.get();

            return new InventoryResponse(
                    item.getProductId(),
                    item.getQuantity()
            );
        }

        InventoryItem saved = repo.save(
                new InventoryItem(productId, initialQuantity)
        );

        return new InventoryResponse(
                saved.getProductId(),
                saved.getQuantity()
        );
    }

    /**
     * delta negative = reserve (checkout), positive = restock (compensation).
     * Pessimistic row lock makes the read-check-write atomic against
     * concurrent adjustments for the same product — this is the exact
     * protection the inventory operation must remain idempotent and bounded;
     * stock has always had it and still does here.
     */
    @Transactional
    public InventoryResponse adjust(Long productId, int delta) {
        return adjustInternal(productId, delta, null, null);
    }

    /**
     * Idempotent adjustment.
     *
     * @param operationId caller-chosen unique id. A second call with an id already
     *        recorded is a no-op that returns the current quantity — safe to
     *        retry or replay.
     * @param undoOf the operation id of the reservation this call reverses
     *        (a release). If that reservation was never recorded — its request
     *        timed out before reaching us — there is nothing to undo, so nothing
     *        is added back; a tombstone is stored so the reservation cannot be
     *        applied later if the original request is still in flight.
     *
     * Everything runs after the product row is locked, so concurrent calls for
     * one product are serialised and the "already recorded?" checks cannot race.
     */
    @Transactional
    public InventoryResponse adjust(
            Long productId,
            int delta,
            String operationId,
            String undoOf
    ) {
        return adjustInternal(productId, delta, operationId, undoOf);
    }

    private InventoryResponse adjustInternal(
            Long productId,
            int delta,
            String operationId,
            String undoOf
    ) {
        java.util.Optional<InventoryItem> locked =
                repo.findByProductIdForUpdate(productId);

        if (operations != null && operationId != null) {
            java.util.Optional<InventoryOperation> existing = operations.findById(operationId);
            if (existing.isPresent()) {
                InventoryOperation prior = existing.get();
                if (!prior.getProductId().equals(productId) || prior.getDelta() != delta) {
                    throw new IdempotencyConflictException(
                            "Operation id was already used for a different inventory adjustment");
                }
                return currentQuantity(productId, locked);
            }
        }

        if (operations != null
                && undoOf != null
                && !operations.existsById(undoOf)) {

            operations.save(
                    new InventoryOperation(
                            undoOf,
                            productId,
                            0
                    )
            );

            if (operationId != null) {
                operations.save(
                        new InventoryOperation(
                                operationId,
                                productId,
                                0
                        )
                );
            }

            return currentQuantity(productId, locked);
        }

        InventoryItem item = locked
                .orElseThrow(() -> new InventoryItemNotFoundException(productId));

        int newQuantity = item.getQuantity() + delta;

        if (newQuantity < 0) {
            throw new InsufficientInventoryException(
                    productId,
                    item.getQuantity(),
                    -delta
            );
        }

        item.setQuantity(newQuantity);

        InventoryItem saved = repo.save(item);

        if (operations != null && operationId != null) {
            operations.save(
                    new InventoryOperation(
                            operationId,
                            productId,
                            delta
                    )
            );
        }

        return new InventoryResponse(
                saved.getProductId(),
                saved.getQuantity()
        );
    }

    private static InventoryResponse currentQuantity(
            Long productId,
            java.util.Optional<InventoryItem> item
    ) {
        return new InventoryResponse(
                productId,
                item.map(InventoryItem::getQuantity).orElse(0)
        );
    }
}