package com.catalogix.inventory.svc;

import com.catalogix.inventory.dto.InventoryResponse;
import com.catalogix.inventory.model.InventoryItem;
import com.catalogix.inventory.model.InventoryOperation;
import com.catalogix.inventory.exception.IdempotencyConflictException;
import com.catalogix.inventory.repository.InventoryItemRepository;
import com.catalogix.inventory.repository.InventoryOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Idempotent reserve / release: repeats and reversals of operations that never happened. */
class InventorySvcIdempotencyTest {

    private InventoryItemRepository items;
    private InventoryOperationRepository operations;
    private InventorySvc svc;

    @BeforeEach
    void setUp() {
        items = mock(InventoryItemRepository.class);
        operations = mock(InventoryOperationRepository.class);
        svc = new InventorySvc(items, operations);
        when(items.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void appliesANewOperationAndRecordsItsId() {
        when(items.findByProductIdForUpdate(1L)).thenReturn(Optional.of(new InventoryItem(1L, 10)));
        when(operations.existsById("op-1")).thenReturn(false);

        InventoryResponse response = svc.adjust(1L, -3, "op-1", null);

        assertThat(response.getQuantity()).isEqualTo(7);
        verify(operations).save(any(InventoryOperation.class));
    }

    @Test
    void aRepeatedOperationIdIsANoOp() {
        when(items.findByProductIdForUpdate(1L)).thenReturn(Optional.of(new InventoryItem(1L, 7)));
        when(operations.findById("op-1"))
                .thenReturn(Optional.of(new InventoryOperation("op-1", 1L, -3)));

        InventoryResponse response = svc.adjust(1L, -3, "op-1", null);

        assertThat(response.getQuantity()).isEqualTo(7);
        verify(items, never()).save(any());
    }

    @Test
    void reusingAnOperationIdForDifferentDeltaIsRejected() {
        when(items.findByProductIdForUpdate(1L)).thenReturn(Optional.of(new InventoryItem(1L, 7)));
        when(operations.findById("op-1"))
                .thenReturn(Optional.of(new InventoryOperation("op-1", 1L, -3)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> svc.adjust(1L, -4, "op-1", null))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(items, never()).save(any());
    }

    @Test
    void releasingAReservationThatWasAppliedAddsTheStockBack() {
        when(items.findByProductIdForUpdate(1L)).thenReturn(Optional.of(new InventoryItem(1L, 7)));
        when(operations.existsById("release-1")).thenReturn(false);
        when(operations.existsById("reserve-1")).thenReturn(true);

        InventoryResponse response = svc.adjust(1L, 3, "release-1", "reserve-1");

        assertThat(response.getQuantity()).isEqualTo(10);
    }

    @Test
    void releasingAReservationThatNeverArrivedAddsNothingAndBlocksALateArrival() {
        when(items.findByProductIdForUpdate(1L)).thenReturn(Optional.of(new InventoryItem(1L, 10)));
        when(operations.existsById("release-1")).thenReturn(false);
        when(operations.existsById("reserve-1")).thenReturn(false);

        InventoryResponse response = svc.adjust(1L, 3, "release-1", "reserve-1");

        assertThat(response.getQuantity()).isEqualTo(10);
        verify(items, never()).save(any());
        // tombstone for the reservation + the release itself
        verify(operations).save(org.mockito.ArgumentMatchers.argThat(o -> "reserve-1".equals(o.getOperationId()) && o.getDelta() == 0));
        verify(operations).save(org.mockito.ArgumentMatchers.argThat(o -> "release-1".equals(o.getOperationId())));
    }

    @Test
    void callsWithoutOperationIdsBehaveExactlyAsBefore() {
        when(items.findByProductIdForUpdate(1L)).thenReturn(Optional.of(new InventoryItem(1L, 5)));

        assertThat(svc.adjust(1L, 10).getQuantity()).isEqualTo(15);
        verify(operations, never()).save(any());
    }
}
