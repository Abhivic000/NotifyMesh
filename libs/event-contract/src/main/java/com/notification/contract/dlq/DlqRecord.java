package com.notification.contract.dlq;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Published to a channel's DLQ topic (notification.email.dlq etc.) after retry
 * exhaustion (PRD section 34). Carries everything an operator would need to inspect
 * and manually retry the failure later, without needing to cross-reference other
 * systems.
 */
public record DlqRecord(
        JsonNode originalMessage,
        String eventId,
        String notificationId,
        String channel,
        int attemptCount,
        String lastError,
        String errorCode,
        Instant firstFailureAt,
        Instant lastFailureAt,
        String correlationId
) {
}
