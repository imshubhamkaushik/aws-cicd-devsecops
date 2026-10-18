package com.catalogix.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Error bodies use the same shape as every other service:
// {"message", "timestamp", "status"[, "errors"]}.
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
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(InsufficientInventoryException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(InventoryItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(InventoryItemNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }


    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, "Internal server error");
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
