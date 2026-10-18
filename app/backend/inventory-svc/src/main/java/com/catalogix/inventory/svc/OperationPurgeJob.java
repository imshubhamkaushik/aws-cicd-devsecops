package com.catalogix.inventory.svc;

import com.catalogix.inventory.repository.InventoryOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Trims the idempotency ledger. An operation id only has to be remembered for as long
 * as a duplicate of that call could still arrive: checkout's retries and outbox give
 * up long before the default 30-day retention.
 */
@Component
public class OperationPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(OperationPurgeJob.class);

    private final InventoryOperationRepository operations;
    private final Duration retention;

    public OperationPurgeJob(InventoryOperationRepository operations,
                             @Value("${INVENTORY_OPERATION_RETENTION_DAYS:30}") long retentionDays) {
        this.operations = operations;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${INVENTORY_OPERATION_PURGE_CRON:0 45 3 * * *}")
    @Transactional
    public void purge() {
        int removed = operations.deleteOlderThan(Instant.now().minus(retention));
        log.info("Inventory operation ledger purge: removed {} entries", removed);
    }
}
