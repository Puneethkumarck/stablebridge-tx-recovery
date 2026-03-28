package com.stablebridge.txrecovery.application.controller;

import java.time.Instant;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.stablebridge.txrecovery.api.model.ErrorResponse;
import com.stablebridge.txrecovery.domain.exception.StrException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StrException.class)
    public ResponseEntity<ErrorResponse> handleStrException(StrException ex, HttpServletRequest request) {
        var status = mapErrorCodeToStatus(ex.getErrorCode());

        if (status.is5xxServerError()) {
            log.error("Domain error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        }

        var response = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (first, _) -> first));

        var response = ErrorResponse.builder()
                .errorCode("STR-4000")
                .message("Validation failed")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .details(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        var response = ErrorResponse.builder()
                .errorCode("STR-4000")
                .message("Malformed request body")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        var details = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage(),
                        (first, _) -> first));

        var response = ErrorResponse.builder()
                .errorCode("STR-4000")
                .message("Constraint violation")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .details(details)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        var response = ErrorResponse.builder()
                .errorCode("STR-5000")
                .message("Internal server error")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private HttpStatus mapErrorCodeToStatus(String errorCode) {
        if (errorCode == null || errorCode.length() < 7) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        var numericPart = errorCode.substring(4);

        return switch (numericPart.substring(0, 2)) {
            case "40" -> mapClientErrorCode(numericPart);
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private HttpStatus mapClientErrorCode(String numericPart) {
        return switch (numericPart.substring(0, 3)) {
            case "400" -> HttpStatus.BAD_REQUEST;
            case "404" -> HttpStatus.NOT_FOUND;
            case "409" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
