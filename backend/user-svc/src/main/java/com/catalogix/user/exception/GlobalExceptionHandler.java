package com.catalogix.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MESSAGE = "message";
    private static final String TIMESTAMP = "timestamp";
    private static final String STATUS = "status";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, "Validation failed");
        body.put("errors", errors);
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(STATUS, HttpStatus.CONFLICT.value());
        body.put(TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.TOO_MANY_REQUESTS.value());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAddressNotFound(AddressNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now());
        body.put(STATUS, HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
