package com.notification.event.controller;

import com.notification.contract.event.EventEnvelope;
import com.notification.event.dto.EventIngestionResult;
import com.notification.event.service.EventIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP concerns only (PRD section 77) - validation is declarative (@Valid), and every
 * bit of actual logic (idempotency check, the outbox transaction) lives in the service.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping
    public ResponseEntity<EventIngestionResult> submitEvent(@Valid @RequestBody EventEnvelope envelope) {
        EventIngestionResult result = eventIngestionService.ingest(envelope);
        // 202: durable acceptance, not proof of downstream processing (PRD section 65) -
        // by the time this returns, the event and its outbox row are already committed,
        // but nothing has necessarily reached Kafka yet.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}
