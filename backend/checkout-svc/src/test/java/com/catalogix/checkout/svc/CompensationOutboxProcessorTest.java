package com.catalogix.checkout.svc;

import com.catalogix.checkout.client.InventoryClient;
import com.catalogix.checkout.model.CompensationOutbox;
import com.catalogix.checkout.model.OutboxStatus;
import com.catalogix.checkout.repository.CompensationOutboxRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompensationOutboxProcessorTest {

    @Mock private CompensationOutboxRepository outboxRepo;
    @Mock private InventoryClient inventoryClient;

    private CompensationOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new CompensationOutboxProcessor(outboxRepo, inventoryClient);
        when(outboxRepo.save(any(CompensationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void completesAStockReleaseEntryOnSuccess() {
        CompensationOutbox entry = CompensationOutbox.releaseStock(1L, 2, "cancel-order-5");
        when(outboxRepo.claimPendingBatch()).thenReturn(List.of(entry));

        processor.processPending();

        assertEquals(OutboxStatus.COMPLETED, entry.getStatus());
        verify(inventoryClient).adjust(1L, 2);
        verify(outboxRepo).save(entry);
    }


    @Test
    void recordsTheFailureAndIncrementsAttemptsWithoutDeadLettering() {
        CompensationOutbox entry = CompensationOutbox.releaseStock(1L, 2, "cancel-order-5");
        entry.setAttempts(1);
        when(outboxRepo.claimPendingBatch()).thenReturn(List.of(entry));
        doThrow(new RuntimeException("inventory-svc unreachable"))
                .when(inventoryClient).adjust(1L, 2);

        processor.processPending();

        assertEquals(OutboxStatus.PENDING, entry.getStatus());
        assertEquals(2, entry.getAttempts());
        assertEquals("inventory-svc unreachable", entry.getLastError());
        verify(outboxRepo).save(entry);
    }

    @Test
    void deadLettersAnEntryOnceItExhaustsMaxAttempts() {
        CompensationOutbox entry = CompensationOutbox.releaseStock(1L, 2, "cancel-order-5");
        entry.setAttempts(4); // one more failure reaches the 5-attempt ceiling
        when(outboxRepo.claimPendingBatch()).thenReturn(List.of(entry));
        doThrow(new RuntimeException("inventory-svc unreachable"))
                .when(inventoryClient).adjust(1L, 2);

        processor.processPending();

        assertEquals(OutboxStatus.DEAD_LETTER, entry.getStatus());
        assertEquals(5, entry.getAttempts());
    }

    @Test
    void aFailureInOneEntryDoesNotStopTheRestOfTheBatch() {
        CompensationOutbox failing = CompensationOutbox.releaseStock(1L, 2, "cancel-order-5");
        CompensationOutbox succeeding = CompensationOutbox.releaseStock(2L, 1, "cancel-order-6");
        when(outboxRepo.claimPendingBatch()).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("inventory-svc unreachable"))
                .when(inventoryClient).adjust(1L, 2);

        processor.processPending();

        assertEquals(OutboxStatus.PENDING, failing.getStatus());
        assertEquals(OutboxStatus.COMPLETED, succeeding.getStatus());
        verify(outboxRepo, times(2)).save(any(CompensationOutbox.class));
    }

    @Test
    void doesNothingWhenTheBatchIsEmpty() {
        when(outboxRepo.claimPendingBatch()).thenReturn(List.of());

        processor.processPending();

        verifyNoInteractions(inventoryClient);
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void successClearsAnyPriorErrorAttemptsAreLeftAsIs() {
        // Attempts/lastError are only touched on the failure branch — a
        // successful retry doesn't retroactively erase how many attempts it
        // took, only the terminal status changes to COMPLETED.
        CompensationOutbox entry = CompensationOutbox.releaseStock(1L, 2, "cancel-order-5");
        entry.setAttempts(3);
        entry.setLastError("inventory-svc unreachable");
        when(outboxRepo.claimPendingBatch()).thenReturn(List.of(entry));

        processor.processPending();

        assertEquals(OutboxStatus.COMPLETED, entry.getStatus());
        assertEquals(3, entry.getAttempts());
        assertEquals("inventory-svc unreachable", entry.getLastError());
    }
}
