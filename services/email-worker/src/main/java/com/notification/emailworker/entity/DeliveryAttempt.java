package com.notification.emailworker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private String notificationId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private String status;

    @Column(name = "provider_response")
    private String providerResponse;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(String notificationId, int attemptNumber, String status,
                            String providerResponse, String errorCode) {
        this.notificationId = notificationId;
        this.attemptNumber = attemptNumber;
        this.status = status;
        this.providerResponse = providerResponse;
        this.errorCode = errorCode;
    }

    @PrePersist
    void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }
}
