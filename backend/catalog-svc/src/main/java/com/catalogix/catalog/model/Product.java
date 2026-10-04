package com.catalogix.catalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

// Owns what a product is (name, description, price, category, owner). Stock lives in
// inventory-svc: product data is read-heavy and cached, stock changes on every order.
@Entity
@Table(name = "products")
public class Product {

    public enum ModerationStatus { PUBLISHED, PENDING_REVIEW, REJECTED, SUSPENDED }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String category = "GENERAL";

    // Id of the user (from user-svc) who created this listing. Only this
    // user, or an ADMIN, may delete it.
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    // Optional URL pasted by the creator; there is no upload pipeline.
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 30)
    private ModerationStatus moderationStatus = ModerationStatus.PUBLISHED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Product() {}

    public Product(String name, String description, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public Product(String name, String description, BigDecimal price, String category, Long ownerId) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.ownerId = ownerId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public ModerationStatus getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(ModerationStatus moderationStatus) { this.moderationStatus = moderationStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
