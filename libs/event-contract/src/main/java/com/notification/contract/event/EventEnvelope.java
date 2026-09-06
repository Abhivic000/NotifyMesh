package com.notification.contract.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * The standard event envelope every client publishes (PRD section 11) and every
 * downstream consumer (notification-service, and eventually other services) reads.
 * A record, not a class: this is a pure, immutable wire-format value - there is no
 * behavior here, and there must never be any. Business logic belongs to the services
 * that consume this envelope, never to the envelope itself.
 * <p>
 * {@code payload} is kept as a generic {@link JsonNode} rather than a concrete type
 * because event types are meant to be added without changing this shared module
 * (PRD section 13) - if this class had to know every payload shape, every new event
 * type would force a change here, defeating the point of a shared contract.
 */
public record EventEnvelope(

        @NotBlank(message = "eventId must not be blank")
        String eventId,

        @NotBlank(message = "eventType must not be blank")
        String eventType,

        @NotBlank(message = "source must not be blank")
        String source,

        @NotBlank(message = "version must not be blank")
        String version,

        @NotNull(message = "timestamp must not be null")
        Instant timestamp,

        // Optional by design (PRD section 11 lists it as required conceptually, but an
        // absent correlationId is recoverable - the ingestion layer generates one rather
        // than rejecting the request outright; see PRD section 52).
        String correlationId,

        @NotNull(message = "payload must not be null")
        JsonNode payload
) {
}
