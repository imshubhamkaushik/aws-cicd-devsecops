package com.catalogix.inventory.exception;

/** Thrown when a caller is authenticated but not authorized for the action — e.g. a
 *  regular user token hitting an endpoint that requires a SYSTEM-minted token. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
