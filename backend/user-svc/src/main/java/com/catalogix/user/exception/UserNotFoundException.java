package com.catalogix.user.exception;

/** Target user of an admin action does not exist (404 — NOT a 401, which would log the admin out). */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User " + id + " not found");
    }
}
