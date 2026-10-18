package com.catalogix.inventory.svc;

import com.catalogix.inventory.dto.InventoryResponse;
import com.catalogix.inventory.exception.InsufficientInventoryException;
import com.catalogix.inventory.exception.InventoryItemNotFoundException;
import com.catalogix.inventory.model.InventoryItem;
import com.catalogix.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventorySvcTest {

    private InventoryItemRepository repo;
    private InventorySvc svc;

    @BeforeEach
    void setUp() {
        repo = mock(InventoryItemRepository.class);
        svc = new InventorySvc(repo);
    }

    @Test
    void adjustIncreasesQuantityOnRestock() {

        InventoryItem item = new InventoryItem(1L, 5);

        when(repo.findByProductIdForUpdate(1L))
                .thenReturn(Optional.of(item));

        when(repo.save(any(InventoryItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        InventoryResponse response = svc.adjust(1L, 10);

        assertThat(response.getQuantity()).isEqualTo(15);
    }

    @Test
    void adjustThrowsWhenInsufficientStock() {

        InventoryItem item = new InventoryItem(1L, 3);

        when(repo.findByProductIdForUpdate(1L))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> svc.adjust(1L, -5))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void adjustThrowsWhenProductDoesNotExist() {

        when(repo.findByProductIdForUpdate(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.adjust(99L, -1))
                .isInstanceOf(InventoryItemNotFoundException.class);
    }

    @Test
    void initReturnsExistingItemIfAlreadyPresent() {

        InventoryItem item = new InventoryItem(1L, 10);

        when(repo.existsById(1L)).thenReturn(true);
        when(repo.findById(1L)).thenReturn(Optional.of(item));

        InventoryResponse response = svc.init(1L, 20);

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getQuantity()).isEqualTo(10);
    }

    @Test
    void initCreatesNewItemIfMissing() {

        InventoryItem item = new InventoryItem(1L, 10);

        when(repo.existsById(1L)).thenReturn(false);
        when(repo.save(any(InventoryItem.class)))
                .thenReturn(item);

        InventoryResponse response = svc.init(1L, 10);

        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getQuantity()).isEqualTo(10);
    }
}