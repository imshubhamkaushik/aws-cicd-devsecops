package com.catalogix.checkout.svc;

import com.catalogix.checkout.client.InventoryClient;
import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.CompensationType;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Retries failed stock-compensation calls from the transactional outbox. */
@Service
public class CompensationOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(CompensationOutboxProcessor.class);
    private static final int MAX_ATTEMPTS = 5;

    private final CompensationOutboxRepository outboxRepo;
    private final InventoryClient inventoryClient;

    public CompensationOutboxProcessor(CompensationOutboxRepository outboxRepo, InventoryClient inventoryClient) {
        this.outboxRepo = outboxRepo;
        this.inventoryClient = inventoryClient;
    }

    @Scheduled(fixedDelayString = "${OUTBOX_POLL_INTERVAL_MS:5000}")
    @Transactional
    public void processPending() {
        List<CompensationOutbox> batch = outboxRepo.claimPendingBatch();

        for (CompensationOutbox entry : batch) {
            try {
                if (entry.getType() == CompensationType.RELEASE_STOCK) {
                    if (entry.getOperationId() == null && entry.getUndoOf() == null) {
                        inventoryClient.adjust(entry.getProductId(), entry.getDelta());
                    } else {
                        inventoryClient.adjust(
                                entry.getProductId(), entry.getDelta(), entry.getOperationId(), entry.getUndoOf());
                    }
                }
                entry.setStatus(OutboxStatus.COMPLETED);
            } catch (RuntimeException e) {
                entry.setAttempts(entry.getAttempts() + 1);
                entry.setLastError(e.getMessage());
                if (entry.getAttempts() >= MAX_ATTEMPTS) {
                    entry.setStatus(OutboxStatus.DEAD_LETTER);
                    log.error("Compensation outbox entry {} exhausted retries ({}): {}",
                            entry.getId(), entry.getType(), e.getMessage());
                } else {
                    log.warn("Compensation outbox entry {} attempt {} failed ({}): {}",
                            entry.getId(), entry.getAttempts(), entry.getType(), e.getMessage());
                }
            }
            outboxRepo.save(entry);
        }
    }
}
