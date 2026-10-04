package com.catalogix.inventory.dto;

/**
 * delta is signed: negative reserves stock (checkout), positive restocks
 * (compensation / cancellation).
 */
public class AdjustInventoryRequest {
    private int delta;

    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
}
