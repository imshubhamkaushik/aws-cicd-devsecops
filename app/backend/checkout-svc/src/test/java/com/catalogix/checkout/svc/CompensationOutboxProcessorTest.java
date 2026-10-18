package com.catalogix.checkout.svc;

import com.catalogix.checkout.client.InventoryClient;
import com.catalogix.checkout.model.CompensationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompensationOutboxProcessorTest {

    @Mock private CompensationOutboxStateService stateService;
    @Mock private InventoryClient inventoryClient;
    private CompensationOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new CompensationOutboxProcessor(stateService, inventoryClient);
    }

    private static CompensationOutboxStateService.CompensationTask task(long id, int attempts) {
        return new CompensationOutboxStateService.CompensationTask(
                id, CompensationType.RELEASE_STOCK, 11L, 2, "release-" + id, "reserve-" + id, attempts);
    }

    @Test
    void completesAStockReleaseEntryOnSuccess() {
        when(stateService.claimPendingBatch()).thenReturn(List.of(task(1L, 0)));
        processor.processPending();
        verify(inventoryClient).adjust(11L, 2, "release-1", "reserve-1");
        verify(stateService).markCompleted(1L);
        verify(stateService, never()).recordFailure(anyLong(), anyString());
    }

    @Test
    void recordsFailureThroughStateServiceAndContinuesProcessingBatch() {
        when(stateService.claimPendingBatch()).thenReturn(List.of(task(1L, 1), task(2L, 0)));
        doThrow(new RuntimeException("inventory-svc unreachable"))
                .when(inventoryClient).adjust(11L, 2, "release-1", "reserve-1");

        processor.processPending();

        verify(stateService).recordFailure(1L, "inventory-svc unreachable");
        verify(stateService).markCompleted(2L);
    }

    @Test
    void usesStableFallbackOperationIdForHistoricalRowsWithoutAnOperationId() {
        when(stateService.claimPendingBatch()).thenReturn(List.of(
                new CompensationOutboxStateService.CompensationTask(
                        25L, CompensationType.RELEASE_STOCK, 7L, 1, null, null, 0)));
        processor.processPending();
        verify(inventoryClient).adjust(7L, 1, "compensation-outbox-25", null);
    }

    @Test
    void doesNothingWhenTheBatchIsEmpty() {
        when(stateService.claimPendingBatch()).thenReturn(List.of());
        processor.processPending();
        verifyNoInteractions(inventoryClient);
        verify(stateService, never()).markCompleted(anyLong());
    }
}
