package com.catalogix.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.*;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String MESSAGE = "message";
    private static final String TIMESTAMP = "timestamp";
    private static final String STATUS = "status";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(fe.getField() + ": " + fe.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, "Validation failed");
        body.put("errors", errors);
        body.put(TIMESTAMP, new Date());
        body.put(STATUS, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(STATUS, HttpStatus.CONFLICT.value());
        body.put(TIMESTAMP, new Date());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(MESSAGE, ex.getMessage());
        body.put(TIMESTAMP, new Date());
        body.put(STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
