package com.notification.emailworker.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import com.notification.emailworker.entity.DlqItem;

import java.time.Instant;
import java.util.UUID;

/** Inspect-one-item DTO - includes the full original message (PRD section 34). */
public record DlqItemDetail(
        UUID id, String notificationId, String eventId, String channel,
        JsonNode originalMessage, int attemptCount, String lastError, String errorCode,
        String correlationId, String status, Instant firstFailureAt, Instant lastFailureAt
) {
    public static DlqItemDetail from(DlqItem item) {
        return new DlqItemDetail(
                item.getId(), item.getNotificationId(), item.getEventId(), item.getChannel(),
                item.getOriginalMessage(), item.getAttemptCount(), item.getLastError(), item.getErrorCode(),
                item.getCorrelationId(), item.getStatus(), item.getFirstFailureAt(), item.getLastFailureAt()
        );
    }
}
