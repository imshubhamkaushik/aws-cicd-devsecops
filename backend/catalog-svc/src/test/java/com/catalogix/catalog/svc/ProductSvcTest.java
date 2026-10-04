package com.catalogix.catalog.svc;

import com.catalogix.catalog.client.InventoryClient;
import com.catalogix.catalog.dto.CreateProductRequest;
import com.catalogix.catalog.dto.PagedResponse;
import com.catalogix.catalog.dto.ProductResponse;
import com.catalogix.catalog.exception.ForbiddenException;
import com.catalogix.catalog.exception.ProductNotFoundException;
import com.catalogix.catalog.model.Product;
import com.catalogix.catalog.repository.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ProductSvcTest {

    @Mock private ProductRepository repo;
    @Mock private InventoryClient inventoryClient;
    @Mock private ProductCacheSvc productCacheSvc;

    private ProductSvc svc;

    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        svc = new ProductSvc(repo, inventoryClient, productCacheSvc);
    }

    private Product product(Long id, String name, String price, String category, Long ownerId) {
        Product p = new Product(name, "A " + name.toLowerCase(), new BigDecimal(price), category, ownerId);
        p.setId(id);
        return p;
    }

    // ---- search ----

    @Test
    void searchMergesLiveStockIntoEachResult() {
        Pageable pageable = PageRequest.of(0, 20);
        Product p = product(1L, "Phone", "100.00", "ELECTRONICS", 42L);
        when(repo.search("", "", null, null, true, null, pageable)).thenReturn(new PageImpl<>(List.of(p), pageable, 1));
        when(inventoryClient.fetchQuantity(1L, TOKEN)).thenReturn(7);

        PagedResponse<ProductResponse> result = svc.search(null, null, null, null, null, pageable, TOKEN);

        assertEquals(1, result.getContent().size());
        assertEquals(7, result.getContent().get(0).getStockQuantity());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchTrimsBlankFiltersToNull() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.search("", "", null, null, true, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        svc.search("   ", "  ", null, null, null, pageable, TOKEN);

        verify(repo).search("", "", null, null, true, null, pageable);
    }

    @Test
    void searchPassesThroughTrimmedFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repo.search("phone", "electronics", null, null, true, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        svc.search(" phone ", " electronics ", null, null, null, pageable, TOKEN);

        verify(repo).search("phone", "electronics", null, null, true, null, pageable);
    }

    @Test
    void searchPassesThroughThePriceRange() {
        Pageable pageable = PageRequest.of(0, 20);
        BigDecimal min = new BigDecimal("50.00");
        BigDecimal max = new BigDecimal("150.00");
        when(repo.search("", "", min, max, true, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        svc.search(null, null, min, max, null, pageable, TOKEN);

        verify(repo).search("", "", min, max, true, null, pageable);
    }

    // sortBy, when present, replaces the Pageable's own sort.
    @Test
    void searchBuildsAPriceAscendingSortWhenSortByIsGiven() {
        Pageable requested = PageRequest.of(0, 20); // no explicit sort — the default
        Pageable expectedEffective = PageRequest.of(0, 20, com.catalogix.catalog.dto.ProductSortOption.PRICE_LOW_TO_HIGH.toSort());
        
        when(repo.search("", "", null, null, true, null, expectedEffective)).thenReturn(new PageImpl<>(List.of(), expectedEffective, 0));

        svc.search(null, null, null, null,
                com.catalogix.catalog.dto.ProductSortOption.PRICE_LOW_TO_HIGH, requested, TOKEN);

        verify(repo).search("", "", null, null, true, null, expectedEffective);
    }

    // ---- create ----

    @Test
    @SuppressWarnings("null")
    void createInitializesStockAndReturnsIt() {
        when(repo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Phone");
        req.setPrice(new BigDecimal("100.00"));
        req.setStockQuantity(10);

        ProductResponse resp = svc.create(req, 42L, TOKEN);

        assertEquals(1L, resp.getId());
        assertEquals(10, resp.getStockQuantity());
        assertEquals(42L, resp.getOwnerId());
        verify(inventoryClient).init(1L, 10);
    }

    @Test
    @SuppressWarnings("null")
    void createDefaultsCategoryToGeneralWhenBlank() {
        when(repo.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Phone");
        req.setPrice(new BigDecimal("100.00"));

        svc.create(req, 42L, TOKEN);

        verify(repo).save(argThat((Product p) -> "GENERAL".equals(p.getCategory())));
    }

    @Test
    @SuppressWarnings("null")
    void createDefaultsStockQuantityToZeroWhenOmitted() {
        when(repo.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Phone");
        req.setPrice(new BigDecimal("100.00"));

        ProductResponse resp = svc.create(req, 42L, TOKEN);

        assertEquals(0, resp.getStockQuantity());
        verify(inventoryClient).init(1L, 0);
    }

    // ---- findById ----

    @Test
    void findByIdReturnsProductWithLiveStock() {
        Product p = product(
                1L,
                "Phone",
                "100.00",
                "ELECTRONICS",
                42L
        );

        when(productCacheSvc.cacheCore(1L))
                .thenReturn(Optional.of(
                        new ProductCacheSvc.ProductCore(
                                p.getId(),
                                p.getName(),
                                p.getDescription(),
                                p.getPrice(),
                                p.getCategory(),
                                p.getOwnerId(),
                                p.getImageUrl(),
                                p.getModerationStatus(),
                                p.getCreatedAt()
                        )
                ));

        when(inventoryClient.fetchQuantity(1L, TOKEN)).thenReturn(7);

        Optional<ProductResponse> resp = svc.findById(1L, TOKEN);

        assertTrue(resp.isPresent());
        assertEquals(7, resp.get().getStockQuantity());
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(productCacheSvc.cacheCore(99L))
            .thenReturn(Optional.empty());

        assertTrue(svc.findById(99L, TOKEN).isEmpty());
        
        verifyNoInteractions(inventoryClient);
    }

    // ---- deleteById ----

    @Test
    void deleteByIdAllowsOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(product(1L, "Phone", "100.00", "GENERAL", 42L)));

        assertTrue(svc.deleteById(1L, 42L, "USER"));
        verify(repo).deleteById(1L);
    }

    @Test
    void deleteByIdAllowsAdminEvenWhenNotOwner() {
        when(repo.findById(1L)).thenReturn(Optional.of(product(1L, "Phone", "100.00", "GENERAL", 42L)));

        assertTrue(svc.deleteById(1L, 7L, "ADMIN"));
        verify(repo).deleteById(1L);
    }

    @Test
    void deleteByIdRejectsNonOwnerNonAdmin() {
        when(repo.findById(1L)).thenReturn(Optional.of(product(1L, "Phone", "100.00", "GENERAL", 42L)));

        assertThrows(ForbiddenException.class, () -> svc.deleteById(1L, 7L, "USER"));
        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void deleteByIdReturnsFalseWhenProductDoesNotExist() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertFalse(svc.deleteById(99L, 42L, "USER"));
        verify(repo, never()).deleteById(anyLong());
    }

    // ---- adjustStock ----

    @Test
    void adjustStockThrowsWhenProductDoesNotExist() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> svc.adjustStock(99L, 5, 42L, "USER", TOKEN));
        verifyNoInteractions(inventoryClient);
    }

    // Only the owner or an admin may adjust stock.
    @Test
    void adjustStockRejectsNonOwnerNonAdmin() {
        when(repo.findById(1L)).thenReturn(Optional.of(product(1L, "Phone", "100.00", "GENERAL", 42L)));

        assertThrows(ForbiddenException.class, () -> svc.adjustStock(1L, 5, 7L, "USER", TOKEN));
        verifyNoInteractions(inventoryClient);
    }

    private static Stream<Arguments> authorizedStockAdjustments() {
        return Stream.of(
                Arguments.of(42L, "USER"),
                Arguments.of(7L, "ADMIN")
        );
    }

    @ParameterizedTest(name = "[{index}] userId={0}, role={1}")
    @MethodSource("authorizedStockAdjustments")
    void adjustStockAllowsAuthorizedUsersAndReturnsAdjustedQuantity(
            Long userId,
            String role
    ) {
        when(repo.findById(1L))
                .thenReturn(Optional.of(
                        product(1L, "Phone", "100.00", "GENERAL", 42L)
                ));

        when(inventoryClient.adjust(1L, 5)).thenReturn(15);

        // ProductSvc obtains the normal response first, then replaces its
        // stock quantity with the freshly returned value from adjust().
        when(inventoryClient.fetchQuantity(1L, TOKEN)).thenReturn(999);

        ProductResponse response =
                svc.adjustStock(1L, 5, userId, role, TOKEN);

        assertEquals(15, response.getStockQuantity());
        verify(inventoryClient).adjust(1L, 5);
    }

}
