package com.catalogix.catalog.dto;

import jakarta.validation.constraints.NotNull;

// A positive delta restocks; a negative delta sells or reserves stock.
public class StockAdjustmentRequest {

    @NotNull(message = "delta is required")
    private Integer delta;

    public StockAdjustmentRequest() {
    }

    public StockAdjustmentRequest(Integer delta) {
        this.delta = delta;
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }
}
