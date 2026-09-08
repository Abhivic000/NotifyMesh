package com.notification.smsworker.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.job.NotificationJob;
import com.notification.smsworker.service.SmsWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumer group "sms-worker-group" (PRD section 16). */
@Component
public class SmsJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(SmsJobConsumer.class);

    private final SmsWorkerService smsWorkerService;
    private final ObjectMapper objectMapper;

    public SmsJobConsumer(SmsWorkerService smsWorkerService, ObjectMapper objectMapper) {
        this.smsWorkerService = smsWorkerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${notification.channels.sms-topic}",
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
        smsWorkerService.handle(job);
    }
}
