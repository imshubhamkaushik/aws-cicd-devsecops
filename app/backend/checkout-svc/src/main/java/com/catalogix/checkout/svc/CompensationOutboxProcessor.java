package com.catalogix.checkout.svc;

import com.catalogix.checkout.client.InventoryClient;
import com.catalogix.checkout.model.CompensationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Executes claimed compensations outside a database transaction; every inventory retry is idempotent. */
@Service
public class CompensationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(CompensationOutboxProcessor.class);

    private final CompensationOutboxStateService stateService;
    private final InventoryClient inventoryClient;

    public CompensationOutboxProcessor(CompensationOutboxStateService stateService, InventoryClient inventoryClient) {
        this.stateService = stateService;
        this.inventoryClient = inventoryClient;
    }

    @Scheduled(fixedDelayString = "${OUTBOX_POLL_INTERVAL_MS:5000}")
    public void processPending() {
        for (CompensationOutboxStateService.CompensationTask task : stateService.claimPendingBatch()) {
            try {
                if (task.type() == CompensationType.RELEASE_STOCK) {
                    // Stable operation IDs also cover historical rows created before the ID was persisted.
                    String operationId = task.operationId() == null || task.operationId().isBlank()
                            ? "compensation-outbox-" + task.id()
                            : task.operationId();
                    inventoryClient.adjust(task.productId(), task.delta(), operationId, task.undoOf());
                }
                stateService.markCompleted(task.id());
            } catch (RuntimeException e) {
                stateService.recordFailure(task.id(), e.getMessage());
                log.warn("Compensation outbox entry {} failed; retry state was recorded: {}",
                        task.id(), safeMessage(e));
            }
        }
    }

    private static String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() > 300 ? message.substring(0, 300) : message;
    }
}
