package com.notification.emailworker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.job.NotificationJob;
import com.notification.emailworker.dlq.DlqPublisher;
import com.notification.emailworker.entity.DeliveryAttempt;
import com.notification.emailworker.entity.RetryQueueEntry;
import com.notification.emailworker.idempotency.IdempotencyGuard;
import com.notification.emailworker.integration.NotificationServiceDataAccess;
import com.notification.emailworker.integration.NotificationServiceDataAccess.TemplateView;
import com.notification.emailworker.provider.NotificationProvider.ProviderResult;
import com.notification.emailworker.provider.ProtectedEmailProviderCaller;
import com.notification.emailworker.ratelimit.RateLimiter;
import com.notification.emailworker.repository.DeliveryAttemptRepository;
import com.notification.emailworker.repository.RetryQueueRepository;
import com.notification.emailworker.retry.RetrySchedule;
import com.notification.emailworker.template.TemplateRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The full per-message flow, now including reliability (Phase 6): rate limit -> claim
 * (first attempt only) -> render -> circuit-breaker+timeout-protected provider call ->
 * record attempt -> on success, done; on permanent failure, done (FAILED); on
 * retryable failure, either schedule a retry or - once attempts are exhausted -
 * publish to DLQ. {@link #handle} is the Kafka-triggered entry point (attempt 1);
 * {@link #retryDelivery} is what RetryPoller calls for every attempt after that.
 */
@Service
public class EmailWorkerService {

    private static final Logger log = LoggerFactory.getLogger(EmailWorkerService.class);

    private final IdempotencyGuard idempotencyGuard;
    private final NotificationServiceDataAccess notificationData;
    private final TemplateRenderer templateRenderer;
    private final ProtectedEmailProviderCaller providerCaller;
    private final RateLimiter rateLimiter;
    private final RetrySchedule retrySchedule;
    private final RetryQueueRepository retryQueueRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DlqPublisher dlqPublisher;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public EmailWorkerService(IdempotencyGuard idempotencyGuard,
                               NotificationServiceDataAccess notificationData,
                               TemplateRenderer templateRenderer,
                               ProtectedEmailProviderCaller providerCaller,
                               RateLimiter rateLimiter,
                               RetrySchedule retrySchedule,
                               RetryQueueRepository retryQueueRepository,
                               DeliveryAttemptRepository deliveryAttemptRepository,
                               DlqPublisher dlqPublisher,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.idempotencyGuard = idempotencyGuard;
        this.notificationData = notificationData;
        this.templateRenderer = templateRenderer;
        this.providerCaller = providerCaller;
        this.rateLimiter = rateLimiter;
        this.retrySchedule = retrySchedule;
        this.retryQueueRepository = retryQueueRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.dlqPublisher = dlqPublisher;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public void handle(NotificationJob job) {
        if (!idempotencyGuard.tryClaim(job.notificationId())) {
            log.info("Notification {} already claimed (duplicate delivery), skipping", job.notificationId());
            return;
        }
        attemptDelivery(job, 1, Instant.now());
    }

    public void retryDelivery(RetryQueueEntry entry) {
        NotificationJob job = deserialize(entry.getJobPayload());
        attemptDelivery(job, entry.getAttemptNumber() + 1, entry.getFirstFailureAt());
    }

    private void attemptDelivery(NotificationJob job, int attemptNumber, Instant firstFailureAt) {
        UUID notificationId = UUID.fromString(job.notificationId());

        if (!rateLimiter.isAllowed(job.recipient())) {
            handleRetryableFailure(job, attemptNumber, firstFailureAt,
                    "RATE_LIMITED", "Rate limit exceeded for recipient " + job.recipient());
            return;
        }

        TemplateView template = notificationData.findTemplate(UUID.fromString(job.templateId()));
        String renderedSubject = templateRenderer.render(template.subject(), job.payload());
        String renderedBody = templateRenderer.render(template.body(), job.payload());

        meterRegistry.counter("provider_requests_total", "channel", "EMAIL").increment();
        ProviderResult result = providerCaller.call(job.recipient(), renderedSubject, renderedBody);
        recordAttempt(job.notificationId(), attemptNumber, result);

        if (result.success()) {
            notificationData.markSent(notificationId, result.providerMessageId());
            clearRetryQueueEntry(job.notificationId());
            meterRegistry.counter("notifications_sent_total", "channel", "EMAIL").increment();
            log.info("Email sent notificationId={} eventId={} attempt={} providerMessageId={}",
                    job.notificationId(), job.eventId(), attemptNumber, result.providerMessageId());
            return;
        }

        meterRegistry.counter("provider_failures_total", "channel", "EMAIL", "errorCode", result.errorCode()).increment();

        if (!result.retryable()) {
            notificationData.markFailed(notificationId);
            clearRetryQueueEntry(job.notificationId());
            meterRegistry.counter("notifications_failed_total", "channel", "EMAIL").increment();
            log.warn("Email permanently failed notificationId={} eventId={} errorCode={}",
                    job.notificationId(), job.eventId(), result.errorCode());
            return;
        }

        handleRetryableFailure(job, attemptNumber, firstFailureAt, result.errorCode(), result.rawResponse());
    }

    private void handleRetryableFailure(NotificationJob job, int attemptNumber, Instant firstFailureAt,
                                         String errorCode, String errorDetail) {
        UUID notificationId = UUID.fromString(job.notificationId());

        if (attemptNumber >= retrySchedule.maxAttempts()) {
            dlqPublisher.publish(job, attemptNumber, errorDetail, errorCode, firstFailureAt);
            notificationData.markDlq(notificationId);
            clearRetryQueueEntry(job.notificationId());
            meterRegistry.counter("dlq_total", "channel", "EMAIL", "errorCode", errorCode).increment();
            log.warn("Notification {} exhausted {} attempts (last error {}), moved to DLQ",
                    job.notificationId(), attemptNumber, errorCode);
            return;
        }

        Instant nextAttemptAt = retrySchedule.nextAttemptAt(attemptNumber);
        upsertRetryQueueEntry(job, attemptNumber, nextAttemptAt, errorDetail, firstFailureAt);
        notificationData.markRetrying(notificationId, attemptNumber);
        meterRegistry.counter("retry_total", "channel", "EMAIL", "errorCode", errorCode).increment();
        log.info("Notification {} scheduled for retry #{} at {} (errorCode={})",
                job.notificationId(), attemptNumber + 1, nextAttemptAt, errorCode);
    }

    // Explicit save() after mutating a fetched entity - findByNotificationId returns
    // a detached entity outside of any @Transactional context, so the mutation alone
    // would silently never persist. Same lesson as Phase 2's outbox publisher bug,
    // applied here proactively instead of rediscovering it the hard way again.
    private void upsertRetryQueueEntry(NotificationJob job, int attemptNumber, Instant nextAttemptAt,
                                        String lastError, Instant firstFailureAt) {
        JsonNode jobJson = objectMapper.valueToTree(job);
        retryQueueRepository.findByNotificationId(job.notificationId()).ifPresentOrElse(
                existing -> {
                    existing.reschedule(attemptNumber, nextAttemptAt, lastError);
                    retryQueueRepository.save(existing);
                },
                () -> retryQueueRepository.save(new RetryQueueEntry(
                        job.notificationId(), jobJson, attemptNumber, nextAttemptAt, lastError, firstFailureAt))
        );
    }

    private void clearRetryQueueEntry(String notificationId) {
        retryQueueRepository.findByNotificationId(notificationId).ifPresent(retryQueueRepository::delete);
    }

    private void recordAttempt(String notificationId, int attemptNumber, ProviderResult result) {
        DeliveryAttempt attempt = new DeliveryAttempt(
                notificationId, attemptNumber,
                result.success() ? "SUCCESS" : "FAILED",
                result.rawResponse(), result.errorCode()
        );
        deliveryAttemptRepository.save(attempt);
    }

    private NotificationJob deserialize(JsonNode jobJson) {
        try {
            return objectMapper.treeToValue(jobJson, NotificationJob.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize retry_queue job payload", e);
        }
    }
}
