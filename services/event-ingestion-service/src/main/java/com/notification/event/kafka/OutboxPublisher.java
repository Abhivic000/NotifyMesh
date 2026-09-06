package com.notification.event.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.event.entity.OutboxEvent;
import com.notification.event.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls outbox_events for PENDING rows and publishes each to Kafka - see the Phase 2
 * write-up for why a scheduled poller was chosen over Debezium/CDC for v1.
 * <p>
 * Publishing is at-least-once by construction: if this process crashes after Kafka
 * acknowledges a send but before {@code markPublished()} commits, the row is still
 * PENDING and gets republished next poll. That is a deliberate, accepted trade-off
 * (PRD section 18/20) - downstream consumers must be idempotent, not this publisher.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final int batchSize;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            @Value("${notification.outbox.topic}") String topic,
                            @Value("${notification.outbox.batch-size}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.outbox.poll-interval-ms}")
    public void publishPending() {
        Pageable batch = PageRequest.of(0, batchSize);
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING", batch);

        for (OutboxEvent outboxEvent : pending) {
            publishOne(outboxEvent);
        }
    }

    // No @Transactional here - Spring Data JPA's repository.save() already runs in its
    // own transaction per call, and this method is invoked from within the same class
    // (publishPending -> publishOne), where a proxy-based @Transactional wouldn't take
    // effect anyway (self-invocation bypasses the Spring AOP proxy).
    void publishOne(OutboxEvent outboxEvent) {
        String key = partitionKey(outboxEvent);
        String value;
        try {
            value = objectMapper.writeValueAsString(outboxEvent.getPayload());
        } catch (Exception serializationFailure) {
            // Not retryable - the stored JSON is already malformed, retrying won't fix it.
            log.error("Outbox row {} has unserializable payload, marking FAILED", outboxEvent.getId(), serializationFailure);
            outboxEvent.markFailed(serializationFailure.getMessage());
            outboxEventRepository.save(outboxEvent);
            return;
        }

        try {
            kafkaTemplate.send(topic, key, value).get(5, TimeUnit.SECONDS);
            outboxEvent.markPublished();
            outboxEventRepository.save(outboxEvent);
            log.info("Published outbox event aggregateId={} eventType={} partitionKey={}",
                    outboxEvent.getAggregateId(), outboxEvent.getEventType(), key);
        } catch (ExecutionException | TimeoutException | InterruptedException kafkaUnavailable) {
            // Transient - Kafka being briefly unreachable is expected and must not lose the
            // event. Row stays PENDING; attempt_count/last_error are recorded for visibility.
            log.warn("Failed to publish outbox event {}, will retry: {}",
                    outboxEvent.getId(), kafkaUnavailable.getMessage());
            outboxEvent.recordTransientFailure(kafkaUnavailable.getMessage());
            outboxEventRepository.save(outboxEvent);
            if (kafkaUnavailable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * PRD section 15: partition by userId where ordering matters, so all events for the
     * same user land on the same partition and are processed in order. Falls back to the
     * event's own id when the payload doesn't carry a userId, so partitioning is still
     * deterministic rather than random.
     */
    private String partitionKey(OutboxEvent outboxEvent) {
        JsonNode businessPayload = outboxEvent.getPayload().path("payload");
        JsonNode userId = businessPayload.path("userId");
        return userId.isMissingNode() || userId.isNull()
                ? outboxEvent.getAggregateId()
                : userId.asText();
    }
}
