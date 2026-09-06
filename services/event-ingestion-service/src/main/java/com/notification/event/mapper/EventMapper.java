package com.notification.event.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.event.EventEnvelope;
import com.notification.event.entity.Event;
import com.notification.event.entity.OutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    private final ObjectMapper objectMapper;

    public EventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Event toEntity(EventEnvelope envelope, String resolvedCorrelationId) {
        return new Event(
                envelope.eventId(),
                envelope.eventType(),
                envelope.source(),
                envelope.version(),
                resolvedCorrelationId,
                envelope.payload()
        );
    }

    /**
     * The outbox row's payload is the FULL envelope (eventId/eventType/source/version/
     * correlationId/payload), not just the inner business payload - the publisher
     * (Phase 2) republishes this exact JSON to Kafka unchanged, so downstream consumers
     * need everything here, not only the business fields.
     */
    public OutboxEvent toOutboxEvent(Event event, EventEnvelope originalEnvelope, String resolvedCorrelationId) {
        EventEnvelope canonical = new EventEnvelope(
                originalEnvelope.eventId(),
                originalEnvelope.eventType(),
                originalEnvelope.source(),
                originalEnvelope.version(),
                originalEnvelope.timestamp(),
                resolvedCorrelationId,
                originalEnvelope.payload()
        );
        JsonNode fullEnvelopeJson = objectMapper.valueToTree(canonical);
        return new OutboxEvent(event.getEventId(), event.getEventType(), fullEnvelopeJson);
    }
}
