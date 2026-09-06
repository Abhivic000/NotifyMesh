package com.notification.event.exception;

import com.notification.observability.correlation.CorrelationIdFilter;
import com.notification.observability.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralized error mapping (PRD section 50). Every response body here is the shared
 * ErrorResponse shape from common-observability - callers get one consistent error
 * format regardless of which service or which failure they hit.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                message,
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY)
        );
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Deliberate last-resort catch-all, not the only strategy (PRD section 50 explicitly
     * warns against generic-Exception-only handling) - it exists purely as a backstop
     * beneath specific handlers like the one above, so a truly unexpected failure still
     * returns the standard error shape instead of leaking a stack trace to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY)
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
