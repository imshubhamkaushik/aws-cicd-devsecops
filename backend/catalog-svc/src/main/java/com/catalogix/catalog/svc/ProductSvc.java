package com.catalogix.catalog.svc;

import com.catalogix.catalog.client.InventoryClient;
import com.catalogix.catalog.dto.CreateProductRequest;
import com.catalogix.catalog.dto.PagedResponse;
import com.catalogix.catalog.dto.ProductResponse;
import com.catalogix.catalog.dto.ProductSortOption;
import com.catalogix.catalog.exception.ForbiddenException;
import com.catalogix.catalog.exception.ProductNotFoundException;
import com.catalogix.catalog.model.Product;
import com.catalogix.catalog.model.Product.ModerationStatus;
import com.catalogix.catalog.repository.ProductRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ProductSvc {

        private static final String ADMIN_ROLE = "ADMIN";

        private final ProductRepository repo;
        private final InventoryClient inventoryClient;
        private final ProductCacheSvc productCacheSvc;

        public ProductSvc(
                        ProductRepository repo,
                        InventoryClient inventoryClient,
                        ProductCacheSvc productCacheSvc) {

                this.repo = repo;
                this.inventoryClient = inventoryClient;
                this.productCacheSvc = productCacheSvc;
        }

        /** Caller identity and credentials for operations that need authorization. */
        public record RequesterContext(
                        String bearerToken,
                        Long requesterId,
                        String requesterRole) {
        }

        /** Search without requester context; treated as an ADMIN caller. */
        @Transactional(readOnly = true)
        public PagedResponse<ProductResponse> search(
                        String search,
                        String category,
                        BigDecimal minPrice,
                        BigDecimal maxPrice,
                        ProductSortOption sortBy,
                        Pageable pageable,
                        String bearerToken) {

                return doSearch(
                                search,
                                category,
                                minPrice,
                                maxPrice,
                                sortBy,
                                pageable,
                                new RequesterContext(
                                                bearerToken,
                                                null,
                                                ADMIN_ROLE));
        }

        /** Search with requester context. */
        @Transactional(readOnly = true)
        public PagedResponse<ProductResponse> search(
                        String search,
                        String category,
                        BigDecimal minPrice,
                        BigDecimal maxPrice,
                        ProductSortOption sortBy,
                        Pageable pageable,
                        RequesterContext requester) {

                return doSearch(
                                search,
                                category,
                                minPrice,
                                maxPrice,
                                sortBy,
                                pageable,
                                requester);
        }

        /** Search implementation; private so transactional public methods do not self-invoke each other. */
        private PagedResponse<ProductResponse> doSearch(
                        String search,
                        String category,
                        BigDecimal minPrice,
                        BigDecimal maxPrice,
                        ProductSortOption sortBy,
                        Pageable pageable,
                        RequesterContext requester) {

                String normalizedSearch = StringUtils.hasText(search)
                                ? search.trim()
                                : "";

                String normalizedCategory = StringUtils.hasText(category)
                                ? category.trim()
                                : "";

                /* A supplied sortBy replaces the Pageable's own sort entirely, to avoid ambiguous mixed ordering. */
                Pageable effectivePageable = sortBy != null
                                ? PageRequest.of(
                                                pageable.getPageNumber(),
                                                pageable.getPageSize(),
                                                sortBy.toSort())
                                : pageable;

                Page<Product> page = repo.search(
                                normalizedSearch,
                                normalizedCategory,
                                minPrice,
                                maxPrice,
                                ADMIN_ROLE.equalsIgnoreCase(requester.requesterRole()),
                                requester.requesterId(),
                                effectivePageable);

                /* Known limitation: stock is fetched per product; a batch lookup would scale better. */
                return PagedResponse.from(
                                page,
                                page.getContent()
                                                .stream()
                                                .map(product -> toResponse(
                                                                product,
                                                                requester.bearerToken()))
                                                .toList());
        }

        /** Create without requester role. */
        @Transactional
        public ProductResponse create(
                        CreateProductRequest req,
                        Long ownerId) {

                return doCreate(
                                req,
                                ownerId,
                                ADMIN_ROLE);
        }

        /** Create with requester role. */
        @Transactional
        public ProductResponse create(
                        CreateProductRequest req,
                        Long ownerId,
                        String requesterRole) {

                return doCreate(
                                req,
                                ownerId,
                                requesterRole);
        }

        /** Create implementation; private so transactional public methods do not self-invoke each other. */
        private ProductResponse doCreate(
                        CreateProductRequest req,
                        Long ownerId,
                        String requesterRole) {

                Product product = new Product();

                product.setName(req.getName());
                product.setDescription(req.getDescription());
                product.setPrice(req.getPrice());

                product.setCategory(
                                StringUtils.hasText(req.getCategory())
                                                ? req.getCategory().trim()
                                                : "GENERAL");

                product.setOwnerId(ownerId);
                product.setImageUrl(req.getImageUrl());

                product.setModerationStatus(
                                ADMIN_ROLE.equalsIgnoreCase(requesterRole)
                                                ? ModerationStatus.PUBLISHED
                                                : ModerationStatus.PENDING_REVIEW);

                Product saved = repo.save(product);

                int initialStock = req.getStockQuantity() != null
                                ? req.getStockQuantity()
                                : 0;

                inventoryClient.init(
                                saved.getId(),
                                initialStock);

                ProductResponse response = new ProductResponse(
                                saved.getId(),
                                saved.getName(),
                                saved.getDescription(),
                                saved.getPrice());

                response.setCategory(saved.getCategory());
                response.setStockQuantity(initialStock);
                response.setOwnerId(saved.getOwnerId());
                response.setImageUrl(saved.getImageUrl());
                response.setCreatedAt(saved.getCreatedAt());
                response.setModerationStatus(saved.getModerationStatus());

                return response;
        }

        /** Cached without stock; inventory is fetched live because checkout changes it independently. */
        @Transactional(readOnly = true)
        public Optional<ProductResponse> findById(
                        long id,
                        String bearerToken) {

                return productCacheSvc.cacheCore(id).map(core -> {

                        Integer stock = inventoryClient.fetchQuantity(
                                        id,
                                        bearerToken);

                        ProductResponse response = new ProductResponse(
                                        core.id(),
                                        core.name(),
                                        core.description(),
                                        core.price());

                        response.setCategory(core.category());
                        response.setStockQuantity(stock);
                        response.setOwnerId(core.ownerId());
                        response.setImageUrl(core.imageUrl());
                        response.setCreatedAt(core.createdAt());
                        response.setModerationStatus(core.moderationStatus());

                        return response;
                });
        }

        @CacheEvict(value = "products", key = "#id")
        @Transactional
        public boolean deleteById(
                        long id,
                        Long requesterId,
                        String requesterRole) {

                Optional<Product> existing = repo.findById(id);

                if (existing.isEmpty()) {
                        return false;
                }

                Product product = existing.get();

                boolean isOwner = product.getOwnerId() != null
                                && product.getOwnerId().equals(requesterId);

                boolean isAdmin = ADMIN_ROLE.equalsIgnoreCase(requesterRole);

                if (!isOwner && !isAdmin) {
                        throw new ForbiddenException(
                                        "Only the product's owner or an admin may delete it");
                }

                repo.deleteById(id);

                return true;
        }

        @Transactional
        public ProductResponse moderate(
                        long id,
                        ModerationStatus status) {

                Product product = repo.findById(id)
                                .orElseThrow(() -> new ProductNotFoundException(id));

                product.setModerationStatus(status);

                Product saved = repo.save(product);

                ProductResponse response = new ProductResponse(
                                saved.getId(),
                                saved.getName(),
                                saved.getDescription(),
                                saved.getPrice());

                response.setCategory(saved.getCategory());
                response.setOwnerId(saved.getOwnerId());
                response.setImageUrl(saved.getImageUrl());
                response.setCreatedAt(saved.getCreatedAt());
                response.setModerationStatus(saved.getModerationStatus());

                return response;
        }

        /**
         * Adjusts stock after verifying the caller owns the product or is an admin; the inventory
         * call itself uses a system-minted token.
         */
        @Transactional(readOnly = true)
        public ProductResponse adjustStock(
                        long id,
                        int delta,
                        Long requesterId,
                        String requesterRole,
                        String bearerToken) {

                Product product = repo.findById(id)
                                .orElseThrow(() -> new ProductNotFoundException(id));

                boolean isOwner = product.getOwnerId() != null
                                && product.getOwnerId().equals(requesterId);

                boolean isAdmin = ADMIN_ROLE.equalsIgnoreCase(requesterRole);

                if (!isOwner && !isAdmin) {
                        throw new ForbiddenException(
                                        "Only the product's owner or an admin may adjust its stock");
                }

                Integer newQuantity = inventoryClient.adjust(
                                id,
                                delta);

                ProductResponse response = new ProductResponse(
                                product.getId(),
                                product.getName(),
                                product.getDescription(),
                                product.getPrice());

                response.setCategory(product.getCategory());
                response.setStockQuantity(newQuantity);
                response.setOwnerId(product.getOwnerId());
                response.setImageUrl(product.getImageUrl());
                response.setCreatedAt(product.getCreatedAt());
                response.setModerationStatus(product.getModerationStatus());

                return response;
        }

        private ProductResponse toResponse(
                        Product product,
                        String bearerToken) {

                Integer stock = inventoryClient.fetchQuantity(
                                product.getId(),
                                bearerToken);

                ProductResponse response = new ProductResponse(
                                product.getId(),
                                product.getName(),
                                product.getDescription(),
                                product.getPrice());

                response.setCategory(product.getCategory());
                response.setStockQuantity(stock);
                response.setOwnerId(product.getOwnerId());
                response.setImageUrl(product.getImageUrl());
                response.setCreatedAt(product.getCreatedAt());
                response.setModerationStatus(product.getModerationStatus());

                return response;
        }
}