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
 * Maps to the {@code outbox_events} table (Flyway V2). One row here is written in the
 * exact same transaction as its corresponding {@link Event} row - that atomicity is
 * the entire point of the outbox pattern (see the Phase 1 write-up on the dual-write
 * problem). A separate publisher (Phase 2) reads PENDING rows and publishes them to
 * Kafka; this entity itself knows nothing about Kafka.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode payload;

    @Column(nullable = false)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // required by JPA
    }

    public OutboxEvent(String aggregateId, String eventType, JsonNode payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = "PENDING";
        this.attemptCount = 0;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    /** Transient failure (e.g. Kafka unreachable) - stays PENDING, retried on the next poll. */
    public void recordTransientFailure(String error) {
        this.attemptCount++;
        this.lastError = error;
    }

    /** Permanent failure (e.g. the payload itself can't be serialized) - not worth retrying. */
    public void markFailed(String error) {
        this.attemptCount++;
        this.status = "FAILED";
        this.lastError = error;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
