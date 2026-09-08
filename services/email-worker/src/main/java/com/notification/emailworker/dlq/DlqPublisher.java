package com.notification.emailworker.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.dlq.DlqRecord;
import com.notification.contract.job.NotificationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Publishes an exhausted-retry job to notification.email.dlq (PRD section 34). */
@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String dlqTopic;

    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper,
                         @Value("${notification.channels.email-dlq-topic}") String dlqTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.dlqTopic = dlqTopic;
    }

    public void publish(NotificationJob job, int attemptCount, String lastError, String errorCode,
                         Instant firstFailureAt) {
        DlqRecord record = new DlqRecord(
                objectMapper.valueToTree(job),
                job.eventId(),
                job.notificationId(),
                job.channel(),
                attemptCount,
                lastError,
                errorCode,
                firstFailureAt,
                Instant.now(),
                job.correlationId()
        );
        try {
            kafkaTemplate.send(dlqTopic, job.notificationId(), objectMapper.writeValueAsString(record));
            log.warn("Notification {} moved to DLQ after {} attempts, lastError={}",
                    job.notificationId(), attemptCount, lastError);
        } catch (Exception e) {
            log.error("Failed to publish DLQ record for notification {}", job.notificationId(), e);
        }
    }
}
