package com.catalogix.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import com.catalogix.catalog.model.Product.ModerationStatus;

public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private Integer stockQuantity;
    private Long ownerId;
    private String imageUrl;
    private Instant createdAt;
    private ModerationStatus moderationStatus;

    public ProductResponse() {
    }

    public ProductResponse(
            Long id,
            String name,
            String description,
            BigDecimal price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }


    public ModerationStatus getModerationStatus() { 
        return moderationStatus; 
    }
    
    public void setModerationStatus(ModerationStatus moderationStatus) { 
        this.moderationStatus = moderationStatus; 
    }

}