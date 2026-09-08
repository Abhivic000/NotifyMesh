package com.notification.smsworker.dlq;

import com.notification.smsworker.entity.DlqItem;

import java.time.Instant;
import java.util.UUID;

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
