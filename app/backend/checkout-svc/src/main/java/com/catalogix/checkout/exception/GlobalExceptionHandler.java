package com.catalogix.checkout.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
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

    // Unreadable request bodies (malformed JSON, unknown enum values) fail in Jackson before
    // @Valid runs; without this handler they would surface as a generic 500.
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, "Malformed request body");
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, "Missing required parameter: " + ex.getParameterName());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(OrderNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.NOT_FOUND.value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleProductUnavailable(ProductUnavailableException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AddressUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAddressUnavailable(AddressUnavailableException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }


    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOrderState(InvalidOrderStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.CONFLICT.value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }


    @ExceptionHandler(com.catalogix.checkout.exception.RefundFailedException.class)
    public ResponseEntity<Map<String, Object>> handleRefundFailed(
            com.catalogix.checkout.exception.RefundFailedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.BAD_GATEWAY.value());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        // Never return ex.getMessage(): it can leak DB constraint names or driver wording.
        // The detail goes to the logs only.
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, "Internal server error");
        body.put(TIMESTAMP, Instant.now().toString());
        body.put(STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
