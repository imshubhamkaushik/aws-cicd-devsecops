package com.catalogix.catalog.config;

import com.catalogix.catalog.dto.CreateProductRequest;
import com.catalogix.catalog.repository.ProductRepository;
import com.catalogix.catalog.svc.ProductSvc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds a sample catalogue on startup (gated by SEED_DATA, off by default). Skips entirely if
 * each missing sample product is attempted independently. Goes through ProductSvc.createSeeded()
 * rather than the repository so inventory-svc also gets a stock record. Failed rows are retried
 * on the next restart instead of being permanently skipped after a partial seed.
 */
@Component
public class ProductSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);
    private static final Long SEED_OWNER_ID = 0L;

    private final ProductRepository repo;
    private final ProductSvc productSvc;
    private final ProductSeedLock seedLock;
    private final boolean seedEnabled;

    public ProductSeeder(
            ProductRepository repo,
            ProductSvc productSvc,
            ProductSeedLock seedLock,
            @Value("${SEED_DATA:false}") boolean seedEnabled
    ) {
        this.repo = repo;
        this.productSvc = productSvc;
        this.seedLock = seedLock;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            return;
        }
        seedLock.runLocked(this::seedMissingProducts);
    }

    private void seedMissingProducts() {
        int created = 0;
        int skipped = 0;
        for (SeedProduct sp : SAMPLE_PRODUCTS) {
            if (repo.existsByNameIgnoreCase(sp.name())) {
                skipped++;
                continue;
            }

            try {
                CreateProductRequest req = new CreateProductRequest();
                req.setName(sp.name());
                req.setDescription(sp.description());
                req.setPrice(sp.price());
                req.setCategory(sp.category());
                req.setStockQuantity(sp.stock());
                req.setImageUrl(sp.imageUrl());
                productSvc.createSeeded(req, SEED_OWNER_ID);
                created++;
            } catch (RuntimeException e) {
                // Continue so one bad seed does not prevent unrelated sample rows from appearing.
                // Failed metadata is not committed when inventory initialization fails, and the
                // missing row is retried at the next startup.
                log.warn("Could not seed sample product '{}' (will retry on next startup): {}",
                        sp.name(), e.getMessage());
            }
        }
        log.info("Catalogue seeding finished: {} created, {} already present", created, skipped);
    }

    private record SeedProduct(String name, String description, BigDecimal price,
                                String category, int stock, String imageUrl) {}

    private static final String ELECTRONICS = "Electronics";

    private static final List<SeedProduct> SAMPLE_PRODUCTS = List.of(
            new SeedProduct("Wireless Headphones", "Over-ear, active noise cancellation, 30hr battery",
                    new BigDecimal("2499.00"), ELECTRONICS, 40, null),
            new SeedProduct("Smart Watch", "Heart-rate and sleep tracking, 7-day battery",
                    new BigDecimal("5999.00"), ELECTRONICS, 25, null),
            new SeedProduct("Mechanical Keyboard", "Hot-swappable switches, per-key RGB",
                    new BigDecimal("3499.00"), ELECTRONICS, 15, null),
            new SeedProduct("Steel Water Bottle", "1 litre, vacuum insulated, keeps cold 24hr",
                    new BigDecimal("799.00"), "Home", 100, null),
            new SeedProduct("Ceramic Coffee Mug Set", "Set of 4, microwave and dishwasher safe",
                    new BigDecimal("999.00"), "Home", 60, null),
            new SeedProduct("Cotton Bedsheet Set", "Queen size, 300 thread count, 2 pillow covers",
                    new BigDecimal("1899.00"), "Home", 30, null),
            new SeedProduct("Running Shoes", "Lightweight mesh upper, cushioned sole",
                    new BigDecimal("2999.00"), "Sports", 50, null),
            new SeedProduct("Yoga Mat", "6mm thick, non-slip, carry strap included",
                    new BigDecimal("899.00"), "Sports", 70, null),
            new SeedProduct("Backpack", "20L, laptop compartment, water resistant",
                    new BigDecimal("1799.00"), "Fashion", 35, null),
            new SeedProduct("Paperback Novel Bundle", "Set of 3 bestsellers",
                    new BigDecimal("649.00"), "Books", 45, null)
    );
}
