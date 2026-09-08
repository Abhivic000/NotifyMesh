package com.notification.emailworker.dlq;

import com.notification.emailworker.entity.DlqItem;

import java.time.Instant;
import java.util.UUID;

/** List-view DTO - deliberately excludes the full original message payload. */
public record DlqItemSummary(
        UUID id, String notificationId, String eventId, String channel,
        int attemptCount, String errorCode, String status,
        Instant firstFailureAt, Instant lastFailureAt
) {
    public static DlqItemSummary from(DlqItem item) {
        return new DlqItemSummary(
                item.getId(), item.getNotificationId(), item.getEventId(), item.getChannel(),
                item.getAttemptCount(), item.getErrorCode(), item.getStatus(),
                item.getFirstFailureAt(), item.getLastFailureAt()
        );
    }
}
