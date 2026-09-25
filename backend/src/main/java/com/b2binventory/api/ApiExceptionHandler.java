package com.b2binventory.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    // Handle validation errors (e.g., @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, Object> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    "Validation failed",
                    errors
                ));
    }

    // Handle constraint violations
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        String errors = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    "Constraint violation: " + errors,
                    null
                ));
    }

    // Handle data integrity violations (e.g., unique constraint, foreign key)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String message = "Database constraint violation";
        
        if (e.getMessage() != null) {
            if (e.getMessage().contains("unique") || e.getMessage().contains("duplicate")) {
                message = "A record with this value already exists";
            } else if (e.getMessage().contains("foreign key")) {
                message = "Cannot delete record due to related data";
            }
        }
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(createErrorResponse(
                    HttpStatus.CONFLICT.value(),
                    message,
                    null
                ));
    }

    // Handle IllegalArgumentException (thrown by services)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    e.getMessage() != null ? e.getMessage() : "Invalid request",
                    null
                ));
    }

    // Handle RuntimeException (thrown by controllers for "not found" scenarios)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = e.getMessage();
        
        // Check if it's a "not found" exception
        if (message != null && (message.contains("not found") || message.contains("Not found"))) {
            status = HttpStatus.NOT_FOUND;
        }
        
        return ResponseEntity
                .status(status)
                .body(createErrorResponse(
                    status.value(),
                    message != null ? message : "An error occurred",
                    null
                ));
    }

    // Handle NullPointerException
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "A required value was not provided",
                    null
                ));
    }

    // Handle all other exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    e.getMessage() != null ? e.getMessage() : "Something went wrong",
                    null
                ));
    }

    // Helper method to create consistent error responses
    private Map<String, Object> createErrorResponse(int status, String message, Object details) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("status", status);
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        
        if (details != null) {
            response.put("errors", details);
        }
        
        return response;
    }
}
