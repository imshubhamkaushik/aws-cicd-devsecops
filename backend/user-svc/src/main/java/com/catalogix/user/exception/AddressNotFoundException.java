package com.catalogix.user.exception;

public class AddressNotFoundException extends RuntimeException {
    public AddressNotFoundException(Long id) {
        super("Address " + id + " not found");
    }
}
