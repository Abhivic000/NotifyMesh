package com.notification.pushworker.entity;

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

@Entity
@Table(name = "dlq_items")
public class DlqItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private String notificationId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_message", nullable = false)
    private JsonNode originalMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "first_failure_at", nullable = false)
    private Instant firstFailureAt;

    @Column(name = "last_failure_at", nullable = false)
    private Instant lastFailureAt;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected DlqItem() {
    }

    public DlqItem(String notificationId, String eventId, String channel, JsonNode originalMessage,
                   int attemptCount, String lastError, String errorCode,
                   Instant firstFailureAt, Instant lastFailureAt, String correlationId) {
        this.notificationId = notificationId;
        this.eventId = eventId;
        this.channel = channel;
        this.originalMessage = originalMessage;
        this.attemptCount = attemptCount;
        this.lastError = lastError;
        this.errorCode = errorCode;
        this.firstFailureAt = firstFailureAt;
        this.lastFailureAt = lastFailureAt;
        this.correlationId = correlationId;
        this.status = "PENDING_REVIEW";
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRetried() {
        this.status = "RETRIED";
        this.resolvedAt = Instant.now();
    }

    public void markDiscarded() {
        this.status = "DISCARDED";
        this.resolvedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getChannel() {
        return channel;
    }

    public JsonNode getOriginalMessage() {
        return originalMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Instant getFirstFailureAt() {
        return firstFailureAt;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
