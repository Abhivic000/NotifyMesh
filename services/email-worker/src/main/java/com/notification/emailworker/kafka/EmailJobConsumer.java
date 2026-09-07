package com.notification.emailworker.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.job.NotificationJob;
import com.notification.emailworker.service.EmailWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumer group "email-worker-group" (PRD section 16) - same at-least-once/RECORD-ack pattern as EventConsumer. */
@Component
public class EmailJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailJobConsumer.class);

    private final EmailWorkerService emailWorkerService;
    private final ObjectMapper objectMapper;

    public EmailJobConsumer(EmailWorkerService emailWorkerService, ObjectMapper objectMapper) {
        this.emailWorkerService = emailWorkerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${notification.channels.email-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onJob(String rawMessage) {
        NotificationJob job;
        try {
            job = objectMapper.readValue(rawMessage, NotificationJob.class);
        } catch (Exception malformed) {
            log.error("Failed to deserialize NotificationJob, dropping: {}", rawMessage, malformed);
            return;
        }
        emailWorkerService.handle(job);
    }
}
