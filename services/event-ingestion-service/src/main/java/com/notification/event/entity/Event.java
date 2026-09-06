package com.notification.event.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code events} table (Flyway V1). Plain JPA, no Lombok - every field
 * and constructor here is something you should be able to point at and explain
 * line by line, which matters more for a portfolio project than saved keystrokes.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String version;

    @Column(name = "correlation_id")
    private String correlationId;

    // Hibernate 6's native JSON mapping - no extra library needed. Maps this field
    // straight to/from the jsonb column using Jackson under the hood.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode payload;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Event() {
        // required by JPA
    }

    public Event(String eventId, String eventType, String source, String version,
                 String correlationId, JsonNode payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.source = source;
        this.version = version;
        this.correlationId = correlationId;
        this.payload = payload;
        this.status = "RECEIVED";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markProcessed() {
        this.status = "PROCESSED";
        this.processedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public String getVersion() {
        return version;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
