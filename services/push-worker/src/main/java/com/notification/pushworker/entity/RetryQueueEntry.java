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
@Table(name = "retry_queue")
public class RetryQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false, unique = true)
    private String notificationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "job_payload", nullable = false)
    private JsonNode jobPayload;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "first_failure_at", nullable = false)
    private Instant firstFailureAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RetryQueueEntry() {
    }

    public RetryQueueEntry(String notificationId, JsonNode jobPayload, int attemptNumber,
                            Instant nextAttemptAt, String lastError, Instant firstFailureAt) {
        this.notificationId = notificationId;
        this.jobPayload = jobPayload;
        this.attemptNumber = attemptNumber;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.firstFailureAt = firstFailureAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void reschedule(int attemptNumber, Instant nextAttemptAt, String lastError) {
        this.attemptNumber = attemptNumber;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
    }

    public UUID getId() {
        return id;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public JsonNode getJobPayload() {
        return jobPayload;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getFirstFailureAt() {
        return firstFailureAt;
    }
}
