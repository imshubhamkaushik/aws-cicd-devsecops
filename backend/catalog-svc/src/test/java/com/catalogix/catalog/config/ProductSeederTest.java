package com.catalogix.catalog.config;

import com.catalogix.catalog.dto.CreateProductRequest;
import com.catalogix.catalog.repository.ProductRepository;
import com.catalogix.catalog.svc.ProductSvc;
import com.catalogix.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductSeederTest {

    @Mock private ProductRepository repo;
    @Mock private ProductSvc productSvc;
    @Mock private JwtService jwtService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void doesNothingWhenSeedingIsDisabled(){
        ProductSeeder seeder = new ProductSeeder(repo, productSvc, jwtService, false);
        seeder.run();
        verifyNoInteractions(repo, productSvc, jwtService);
    }

    @Test
    void doesNothingWhenProductsAlreadyExist(){
        when(repo.count()).thenReturn(5L);
        ProductSeeder seeder = new ProductSeeder(repo, productSvc, jwtService, true);

        seeder.run();

        verifyNoInteractions(jwtService);
        verify(productSvc, never()).create(any(), any(), any());
    }

    @Test
    void createsSampleProductsThroughProductSvcWhenCatalogueIsEmpty(){
        when(repo.count()).thenReturn(0L);
        when(jwtService.generateSystemToken()).thenReturn("system.jwt.token");
        ProductSeeder seeder = new ProductSeeder(repo, productSvc, jwtService, true);

        seeder.run();

        // Going through the real ProductSvc.create() (not a direct repo
        // save) is the point — that's what also creates the matching
        // inventory-svc stock record. See ProductSeeder's Javadoc.
        verify(productSvc, atLeast(5)).create(any(CreateProductRequest.class), eq(0L), eq("system.jwt.token"));
    }

    @Test
    void aFailurePartwayThroughDoesNotPropagateAndCrashStartup() {
        when(repo.count()).thenReturn(0L);
        when(jwtService.generateSystemToken()).thenReturn("system.jwt.token");
        // Simulates inventory-svc not being reachable yet — the realistic
        // failure mode this is guarding against, see the Javadoc.
        when(productSvc.create(any(), any(), any())).thenThrow(new RuntimeException("connection refused"));

        ProductSeeder seeder = new ProductSeeder(repo, productSvc, jwtService, true);

        assertDoesNotThrow((Executable) seeder::run);
    }
}
