package com.catalogix.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required and must be positive")
    @DecimalMin(value = "0.0", inclusive = false , message = "Price must be greater than zero")
    private BigDecimal price;

    // Optional — defaults to "GENERAL" in the service layer if blank.
    private String category;

    // Optional — defaults to 0 (out of stock until restocked) if omitted.
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    // Optional. Plain http(s) URL (no javascript:/data: schemes) of at most 500 characters,
    // matching the column length.
    @jakarta.validation.constraints.Size(max = 500, message = "imageUrl must be at most 500 characters")
    @jakarta.validation.constraints.Pattern(regexp = "^(https?://\\S+)?$", message = "imageUrl must be an http(s) URL")
    private String imageUrl;

    public CreateProductRequest() {
        // No-argument constructor required for framework instantiation (e.g., Jackson deserialization)
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

    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
