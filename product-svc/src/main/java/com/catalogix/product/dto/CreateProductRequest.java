package com.catalogix.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @Positive(message = "Price must be positive")
    private double price;

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

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
