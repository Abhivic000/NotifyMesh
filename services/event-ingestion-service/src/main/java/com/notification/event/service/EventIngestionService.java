package com.notification.event.service;

import com.notification.contract.event.EventEnvelope;
import com.notification.event.dto.EventIngestionResult;
import com.notification.event.entity.Event;
import com.notification.event.entity.OutboxEvent;
import com.notification.event.mapper.EventMapper;
import com.notification.event.repository.EventRepository;
import com.notification.event.repository.OutboxEventRepository;
import com.notification.observability.correlation.CorrelationIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EventIngestionService.class);
    private static final String ACCEPTED = "ACCEPTED";

    private final EventRepository eventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EventMapper eventMapper;
    private final MeterRegistry meterRegistry;

    public EventIngestionService(EventRepository eventRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  EventMapper eventMapper,
                                  MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventMapper = eventMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Validates nothing itself (Bean Validation already ran on the envelope before this
     * is called) - this method's whole job is the outbox write: persist the event and
     * its outbox row in one local transaction, so either both exist or neither does.
     * <p>
     * Idempotency (PRD section 20, Layer 1 + 3): a repeat of an already-seen eventId
     * returns the same ACCEPTED result instead of creating a second row. Checked twice
     * on purpose - {@code findByEventId} handles the common repeat case cheaply, and the
     * database's unique constraint on {@code event_id} is the real backstop for the race
     * where two concurrent requests both pass that check before either commits.
     */
    @Transactional
    public EventIngestionResult ingest(EventEnvelope envelope) {
        meterRegistry.counter("events_received_total", "eventType", envelope.eventType()).increment();

        if (eventRepository.findByEventId(envelope.eventId()).isPresent()) {
            log.info("Duplicate event ignored eventId={}", envelope.eventId());
            meterRegistry.counter("events_accepted_total", "eventType", envelope.eventType()).increment();
            return new EventIngestionResult(envelope.eventId(), ACCEPTED);
        }

        String correlationId = resolveCorrelationId(envelope);
        Event event = eventMapper.toEntity(envelope, correlationId);
        OutboxEvent outboxEvent = eventMapper.toOutboxEvent(event, envelope, correlationId);

        try {
            eventRepository.save(event);
            outboxEventRepository.save(outboxEvent);
        } catch (DataIntegrityViolationException raceOnUniqueEventId) {
            log.info("Duplicate event detected via unique constraint eventId={}", envelope.eventId());
            meterRegistry.counter("events_accepted_total", "eventType", envelope.eventType()).increment();
            return new EventIngestionResult(envelope.eventId(), ACCEPTED);
        }

        meterRegistry.counter("events_accepted_total", "eventType", envelope.eventType()).increment();
        log.info("Event accepted eventId={} eventType={}", envelope.eventId(), envelope.eventType());
        return new EventIngestionResult(envelope.eventId(), ACCEPTED);
    }

    /**
     * The envelope's correlationId is optional (clients may omit it); when absent, this
     * falls back to the correlation ID CorrelationIdFilter already resolved for the HTTP
     * request itself, rather than minting an unrelated second ID (PRD sections 11 + 52).
     */
    private String resolveCorrelationId(EventEnvelope envelope) {
        if (envelope.correlationId() != null && !envelope.correlationId().isBlank()) {
            return envelope.correlationId();
        }
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
