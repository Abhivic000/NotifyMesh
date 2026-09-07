package com.notification.emailworker.service;

import com.notification.contract.job.NotificationJob;
import com.notification.emailworker.entity.DeliveryAttempt;
import com.notification.emailworker.idempotency.IdempotencyGuard;
import com.notification.emailworker.integration.NotificationServiceDataAccess;
import com.notification.emailworker.integration.NotificationServiceDataAccess.TemplateView;
import com.notification.emailworker.provider.NotificationProvider;
import com.notification.emailworker.provider.NotificationProvider.ProviderResult;
import com.notification.emailworker.repository.DeliveryAttemptRepository;
import com.notification.emailworker.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The full per-message flow (PRD section 8.4): claim via Redis, render the template,
 * call the provider, record the attempt, update notification state. No retry loop yet
 * - a failure here (retryable or not) ends this attempt as FAILED; Phase 6 adds the
 * actual retry/backoff/DLQ machinery on top of the classification already captured.
 */
@Service
public class EmailWorkerService {

    private static final Logger log = LoggerFactory.getLogger(EmailWorkerService.class);

    private final IdempotencyGuard idempotencyGuard;
    private final NotificationServiceDataAccess notificationData;
    private final TemplateRenderer templateRenderer;
    private final NotificationProvider provider;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    public EmailWorkerService(IdempotencyGuard idempotencyGuard,
                               NotificationServiceDataAccess notificationData,
                               TemplateRenderer templateRenderer,
                               NotificationProvider provider,
                               DeliveryAttemptRepository deliveryAttemptRepository) {
        this.idempotencyGuard = idempotencyGuard;
        this.notificationData = notificationData;
        this.templateRenderer = templateRenderer;
        this.provider = provider;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
    }

    public void handle(NotificationJob job) {
        if (!idempotencyGuard.tryClaim(job.notificationId())) {
            log.info("Notification {} already claimed (duplicate delivery), skipping", job.notificationId());
            return;
        }

        TemplateView template = notificationData.findTemplate(UUID.fromString(job.templateId()));
        String renderedSubject = templateRenderer.render(template.subject(), job.payload());
        String renderedBody = templateRenderer.render(template.body(), job.payload());

        ProviderResult result = provider.send(job.recipient(), renderedSubject, renderedBody);

        UUID notificationId = UUID.fromString(job.notificationId());
        recordAttempt(job.notificationId(), result);

        if (result.success()) {
            notificationData.markSent(notificationId, result.providerMessageId());
            log.info("Email sent notificationId={} eventId={} providerMessageId={}",
                    job.notificationId(), job.eventId(), result.providerMessageId());
        } else {
            notificationData.markFailed(notificationId);
            log.warn("Email delivery failed notificationId={} eventId={} errorCode={} retryable={}",
                    job.notificationId(), job.eventId(), result.errorCode(), result.retryable());
        }
    }

    private void recordAttempt(String notificationId, ProviderResult result) {
        DeliveryAttempt attempt = new DeliveryAttempt(
                notificationId,
                1, // attempt numbering becomes meaningful once Phase 6 adds retries
                result.success() ? "SUCCESS" : "FAILED",
                result.rawResponse(),
                result.errorCode()
        );
        deliveryAttemptRepository.save(attempt);
    }
}
