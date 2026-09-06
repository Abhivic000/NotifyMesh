package com.notification.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.event.EventEnvelope;
import com.notification.contract.job.NotificationJob;
import com.notification.notification.entity.Notification;
import com.notification.notification.entity.Template;
import com.notification.notification.repository.NotificationPreferenceRepository;
import com.notification.notification.repository.NotificationRepository;
import com.notification.notification.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The core of Phase 3: for a consumed event, figure out which channels apply, check
 * preferences, create one notification row per applicable channel, and publish a job
 * for each to its channel topic. Templates ARE the "notification rules" here - there
 * is no separate rules table. An active template existing for (eventType, channel) is
 * what "this channel applies to this event type" means, so adding a new event type
 * later is purely a data change (PRD section 13), never a code change.
 */
@Service
public class NotificationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessingService.class);

    private final TemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final KafkaTemplate<String, String> channelJobKafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String emailTopic;
    private final String smsTopic;
    private final String pushTopic;

    public NotificationProcessingService(TemplateRepository templateRepository,
                                          NotificationRepository notificationRepository,
                                          NotificationPreferenceRepository preferenceRepository,
                                          KafkaTemplate<String, String> channelJobKafkaTemplate,
                                          ObjectMapper objectMapper,
                                          @Value("${notification.channels.email-topic}") String emailTopic,
                                          @Value("${notification.channels.sms-topic}") String smsTopic,
                                          @Value("${notification.channels.push-topic}") String pushTopic) {
        this.templateRepository = templateRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.channelJobKafkaTemplate = channelJobKafkaTemplate;
        this.objectMapper = objectMapper;
        this.emailTopic = emailTopic;
        this.smsTopic = smsTopic;
        this.pushTopic = pushTopic;
    }

    public void process(EventEnvelope envelope) {
        List<Template> applicableTemplates = templateRepository.findByTemplateKeyAndActiveTrue(envelope.eventType());

        if (applicableTemplates.isEmpty()) {
            // Not an error - this is exactly how new event types stay "supported"
            // without a code change: no templates yet means nothing to do yet.
            log.info("No active templates for eventType={}, skipping eventId={}",
                    envelope.eventType(), envelope.eventId());
            return;
        }

        String userId = envelope.payload().path("userId").asText(null);
        if (userId == null) {
            log.warn("Event {} has no userId in payload, cannot resolve recipient/preferences, skipping",
                    envelope.eventId());
            return;
        }

        for (Template template : applicableTemplates) {
            if (!isChannelEnabled(userId, template.getChannel())) {
                log.info("Channel {} disabled by preference for user={}, skipping eventId={}",
                        template.getChannel(), userId, envelope.eventId());
                continue;
            }
            createNotificationAndPublishJob(envelope, template, userId);
        }
    }

    private boolean isChannelEnabled(String userId, String channel) {
        // Default-permissive: no row means enabled (PRD section 29's intent - explicit
        // opt-out, not explicit opt-in).
        return preferenceRepository.findByUserIdAndChannel(userId, channel)
                .map(pref -> pref.isEnabled())
                .orElse(true);
    }

    /**
     * One local transaction for the notification row only - publishing to the channel
     * topic deliberately happens OUTSIDE this transaction, same reasoning as the outbox
     * publisher: a DB write and a Kafka publish still can't be made atomic together, so
     * this accepts the same at-least-once trade-off (a crash between commit and publish
     * means a QUEUED-but-never-actually-published notification - acceptable for v1,
     * would become another outbox row in a more advanced design).
     */
    @Transactional
    void createNotificationAndPublishJob(EventEnvelope envelope, Template template, String userId) {
        Notification notification = new Notification(
                envelope.eventId(), userId, template.getChannel(), template.getId(),
                resolveRecipient(envelope.payload(), template.getChannel(), userId)
        );

        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException alreadyExists) {
            // Idempotency Layer 3 (PRD section 20): a redelivered Kafka message for an
            // event already processed hits the unique (event_id, channel) constraint.
            log.info("Notification already exists for eventId={} channel={}, skipping (redelivery)",
                    envelope.eventId(), template.getChannel());
            return;
        }

        notification.markQueued();
        notificationRepository.save(notification);

        NotificationJob job = new NotificationJob(
                notification.getId().toString(),
                envelope.eventId(),
                envelope.eventType(),
                template.getChannel(),
                template.getId().toString(),
                notification.getRecipient(),
                envelope.correlationId(),
                envelope.payload()
        );
        publishJob(job);
    }

    private void publishJob(NotificationJob job) {
        String topic = switch (job.channel()) {
            case "EMAIL" -> emailTopic;
            case "SMS" -> smsTopic;
            case "PUSH" -> pushTopic;
            default -> throw new IllegalStateException("Unsupported channel: " + job.channel());
        };
        // Fire-and-forget from this method's perspective - errors here are a known gap
        // (see the Javadoc above); Phase 6 revisits this with retry/DLQ semantics.
        channelJobKafkaTemplate.send(topic, job.notificationId(), toJson(job));
    }

    private String toJson(NotificationJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize NotificationJob", e);
        }
    }

    /**
     * Recipient resolution is deliberately stubbed for v1 - there is no user/contact
     * profile service in this architecture (PRD's non-goals exclude building real
     * provider integrations, and no such service is listed among the six). Events may
     * carry a channel-specific hint directly in their payload (email/phone/deviceToken);
     * if absent, a placeholder is synthesized so the pipeline still runs end-to-end for
     * demo purposes. A real system would call an actual user-profile service here.
     */
    private String resolveRecipient(JsonNode payload, String channel, String userId) {
        return switch (channel) {
            case "EMAIL" -> payload.path("email").asText(userId + "@example.com");
            case "SMS" -> payload.path("phone").asText("+00000000000");
            case "PUSH" -> payload.path("deviceToken").asText("unknown-device:" + userId);
            default -> userId;
        };
    }
}
