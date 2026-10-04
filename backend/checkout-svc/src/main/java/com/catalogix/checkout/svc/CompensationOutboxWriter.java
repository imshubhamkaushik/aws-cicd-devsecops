package com.catalogix.checkout.svc;

import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.repository.CompensationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists compensation intent in the transaction boundary the caller needs: enqueue() joins
 * the current transaction, while enqueueIndependent() commits in its own (REQUIRES_NEW) so the
 * entry survives a local rollback after a remote side effect has already committed.
 */
@Service
public class CompensationOutboxWriter {

    private final CompensationOutboxRepository repository;

    public CompensationOutboxWriter(CompensationOutboxRepository repository) {
        this.repository = repository;
    }

    /**
     * Writes inside the caller's transaction, so the compensation intent commits or rolls back
     * with the local state change that requires it (for example order cancellation).
     */
    @Transactional
    public CompensationOutbox enqueue(CompensationOutbox entry) {
        return repository.save(entry);
    }

    /**
     * Writes in a separate transaction. Use when a remote side effect may already have committed
     * but the local transaction is expected to roll back (for example a failed order creation).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompensationOutbox enqueueIndependent(CompensationOutbox entry) {
        return repository.save(entry);
    }
}
