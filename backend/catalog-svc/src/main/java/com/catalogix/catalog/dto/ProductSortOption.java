package com.catalogix.catalog.dto;

import org.springframework.data.domain.Sort;

// Discoverable sort names for GET /products?sortBy=..., instead of raw Spring sort
// syntax and JPA property names.
public enum ProductSortOption {
    PRICE_LOW_TO_HIGH(Sort.by(Sort.Direction.ASC, "price")),
    PRICE_HIGH_TO_LOW(Sort.by(Sort.Direction.DESC, "price")),
    NEWEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    NAME_A_TO_Z(Sort.by(Sort.Direction.ASC, "name"));

    private final Sort sort;

    ProductSortOption(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
