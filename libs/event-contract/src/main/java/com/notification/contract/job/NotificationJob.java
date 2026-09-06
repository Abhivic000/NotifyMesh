package com.notification.contract.job;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The wire format notification-service publishes to a channel topic
 * (notification.email/sms/push) and a future channel worker consumes. Lives in
 * event-contract, same as EventEnvelope, for the same reason: both sides of this
 * contract are separate deployable services that must agree on this shape without
 * depending on each other directly.
 */
public record NotificationJob(
        String notificationId,
        String eventId,
        String eventType,
        String channel,
        String templateId,
        String recipient,
        String correlationId,
        JsonNode payload
) {
}
