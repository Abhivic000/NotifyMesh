package com.notification.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.event.EventEnvelope;
import com.notification.notification.service.NotificationProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "notification-service-group" (PRD section 16) - every instance of
 * this service shares this group, so Kafka splits notification.events' 6 partitions
 * between however many instances are running, and a crashed instance's partitions get
 * reassigned to the survivors (rebalancing). ack-mode is RECORD (application.yml) -
 * the offset only commits after process() returns successfully, so a crash mid-message
 * means Kafka redelivers it to whichever instance picks up that partition next -
 * at-least-once, matching everything else in this system. That's exactly why
 * NotificationProcessingService's idempotency check exists: redelivery is expected,
 * not exceptional.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final NotificationProcessingService processingService;
    private final ObjectMapper objectMapper;

    public EventConsumer(NotificationProcessingService processingService, ObjectMapper objectMapper) {
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${notification.events.topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onEvent(String rawMessage) {
        EventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(rawMessage, EventEnvelope.class);
        } catch (Exception malformed) {
            // Not retryable - re-consuming the same malformed bytes won't fix them.
            // Logged and dropped for v1; Phase 6 revisits this with a real DLQ.
            log.error("Failed to deserialize message, dropping: {}", rawMessage, malformed);
            return;
        }

        log.info("Consumed eventId={} eventType={}", envelope.eventId(), envelope.eventType());
        processingService.process(envelope);
    }
}
