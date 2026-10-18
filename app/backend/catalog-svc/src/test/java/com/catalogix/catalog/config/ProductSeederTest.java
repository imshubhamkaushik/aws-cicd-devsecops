package com.catalogix.catalog.config;

import com.catalogix.catalog.dto.CreateProductRequest;
import com.catalogix.catalog.repository.ProductRepository;
import com.catalogix.catalog.svc.ProductSvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProductSeederTest {

    @Mock private ProductRepository repo;
    @Mock private ProductSvc productSvc;
    @Mock private ProductSeedLock seedLock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(seedLock).runLocked(any(Runnable.class));
    }

    @Test
    void doesNothingWhenSeedingIsDisabled(){
        ProductSeeder seeder = new ProductSeeder(repo, productSvc, seedLock, false);
        seeder.run();
        verifyNoInteractions(repo, productSvc, seedLock);
    }

    @Test
    void doesNothingWhenProductsAlreadyExist(){
        when(repo.existsByNameIgnoreCase(anyString())).thenReturn(true);
        ProductSeeder seeder = new ProductSeeder(repo, productSvc, seedLock, true);

        seeder.run();

        verify(productSvc, never()).createSeeded(any(), any());
    }

    @Test
    void createsSampleProductsThroughProductSvcWhenCatalogueIsEmpty(){
        when(repo.existsByNameIgnoreCase(anyString())).thenReturn(false);
        ProductSeeder seeder = new ProductSeeder(repo, productSvc, seedLock, true);

        seeder.run();

        // The dedicated seeding API publishes sample products and initializes stock through inventory-svc.
        verify(productSvc, atLeast(5)).createSeeded(any(CreateProductRequest.class), eq(0L));
    }

    @Test
    void aFailurePartwayThroughDoesNotPropagateAndCrashStartup() {
        when(repo.existsByNameIgnoreCase(anyString())).thenReturn(false);
        // Simulates inventory-svc not being reachable yet — the missing products are retried later.
        when(productSvc.createSeeded(any(), any())).thenThrow(new RuntimeException("connection refused"));

        ProductSeeder seeder = new ProductSeeder(repo, productSvc, seedLock, true);

        assertDoesNotThrow((Executable) seeder::run);
    }
}
