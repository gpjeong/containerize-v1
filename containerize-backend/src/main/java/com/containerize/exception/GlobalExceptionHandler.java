package com.containerize.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 * Converts exceptions into appropriate HTTP responses with consistent error format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles InvalidFileException - returns 400 Bad Request
     */
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFileException(InvalidFileException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles ServiceException - returns 500 Internal Server Error
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Map<String, String>> handleServiceException(ServiceException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles ConstraintViolationException - returns 422 Unprocessable Entity
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Handles MethodArgumentNotValidException - returns 422 Unprocessable Entity
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> response = new HashMap<>();
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request body");
        response.put("detail", errorMessage);
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Handles ResponseStatusException - returns the appropriate status code
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return new ResponseEntity<>(response, ex.getStatusCode());
    }

    /**
     * Handles HttpRequestMethodNotSupportedException - returns 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", "Method '" + ex.getMethod() + "' is not allowed for this endpoint");

        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }

        return new ResponseEntity<>(response, headers, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Handles NoResourceFoundException (Spring 6.1+) - returns 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFound(NoResourceFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", "Resource not found: " + ex.getResourcePath());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles NoHandlerFoundException - returns 404 Not Found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoHandlerFound(NoHandlerFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles all other unexpected exceptions - returns 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("detail", "Internal server error");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
