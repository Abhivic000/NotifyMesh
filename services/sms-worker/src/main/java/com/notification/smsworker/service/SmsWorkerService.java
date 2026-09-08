package com.notification.smsworker.service;

import com.notification.contract.job.NotificationJob;
import com.notification.smsworker.entity.DeliveryAttempt;
import com.notification.smsworker.idempotency.IdempotencyGuard;
import com.notification.smsworker.integration.NotificationServiceDataAccess;
import com.notification.smsworker.integration.NotificationServiceDataAccess.TemplateView;
import com.notification.smsworker.provider.NotificationProvider;
import com.notification.smsworker.provider.NotificationProvider.ProviderResult;
import com.notification.smsworker.repository.DeliveryAttemptRepository;
import com.notification.smsworker.template.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Same flow as EmailWorkerService - SMS has no subject, only a rendered body. */
@Service
public class SmsWorkerService {

    private static final Logger log = LoggerFactory.getLogger(SmsWorkerService.class);

    private final IdempotencyGuard idempotencyGuard;
    private final NotificationServiceDataAccess notificationData;
    private final TemplateRenderer templateRenderer;
    private final NotificationProvider provider;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    public SmsWorkerService(IdempotencyGuard idempotencyGuard,
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
        String renderedBody = templateRenderer.render(template.body(), job.payload());

        ProviderResult result = provider.send(job.recipient(), renderedBody);

        UUID notificationId = UUID.fromString(job.notificationId());
        recordAttempt(job.notificationId(), result);

        if (result.success()) {
            notificationData.markSent(notificationId, result.providerMessageId());
            log.info("SMS sent notificationId={} eventId={} providerMessageId={}",
                    job.notificationId(), job.eventId(), result.providerMessageId());
        } else {
            notificationData.markFailed(notificationId);
            log.warn("SMS delivery failed notificationId={} eventId={} errorCode={} retryable={}",
                    job.notificationId(), job.eventId(), result.errorCode(), result.retryable());
        }
    }

    private void recordAttempt(String notificationId, ProviderResult result) {
        DeliveryAttempt attempt = new DeliveryAttempt(
                notificationId, 1,
                result.success() ? "SUCCESS" : "FAILED",
                result.rawResponse(), result.errorCode()
        );
        deliveryAttemptRepository.save(attempt);
    }
}
