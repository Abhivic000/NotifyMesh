package com.notification.observability.error;

import java.time.Instant;

/**
 * The one error shape every service in this platform returns (PRD section 41).
 * Deliberately excludes anything that could leak internals: no stack traces, no
 * exception class names, no field-level validation detail beyond a human-readable
 * message - see PRD section 41's explicit "do not expose" list.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
    public static ErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, correlationId);
    }
}
