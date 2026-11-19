package com.example.lets_play.common.exception;


import com.example.lets_play.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 400 ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        if (message.isBlank()) message = "Validation failed";
        return build(HttpStatus.BAD_REQUEST, message, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // Don't echo parser details back to the client
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    // ---- 401 / 403 ----

    @ExceptionHandler({AuthenticationFailedException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex, HttpServletRequest req) {
        String message = ex instanceof AuthenticationFailedException
            ? ex.getMessage() : "Authentication required";
        return build(HttpStatus.UNAUTHORIZED, message, req);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex, HttpServletRequest req) {
        String message = ex instanceof ForbiddenException
            ? ex.getMessage() : "You do not have permission to access this resource";
        return build(HttpStatus.FORBIDDEN, message, req);
    }

    // ---- 404 / 409 ----

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler({DuplicateResourceException.class, DuplicateKeyException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest req) {
        String message = ex instanceof DuplicateResourceException
            ? ex.getMessage() : "A resource with these values already exists";
        return build(HttpStatus.CONFLICT, message, req);
    }

    // ---- catch-all ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        // Spring's own web exceptions (405, 415, 404 for unknown paths...) carry their status
        if (ex instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatusCode code = framework.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            if (status != null && status.is4xxClientError()) {
                return ResponseEntity.status(status)
                    .headers(framework.getHeaders())
                    .body(body(status, defaultMessage(status), req));
            }
        }
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.", req);
    }

    // ---- helpers ----

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(body(status, message, req));
    }

    private ErrorResponse body(HttpStatus status, String message, HttpServletRequest req) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
            message, req.getRequestURI());
    }

    private String defaultMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "HTTP method not supported for this endpoint";
            case UNSUPPORTED_MEDIA_TYPE -> "Content type not supported; use application/json";
            default -> "The request could not be processed";
        };
    }
}