package com.catalogix.checkout.exception;

/**
 * Address lookup failed — not found, or address-svc (part of user-svc)
 * unreachable.
 */
public class AddressUnavailableException extends RuntimeException {
    public AddressUnavailableException(String message) {
        super(message);
    }
}
