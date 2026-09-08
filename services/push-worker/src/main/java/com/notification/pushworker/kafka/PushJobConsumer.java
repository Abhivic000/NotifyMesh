package com.notification.pushworker.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.job.NotificationJob;
import com.notification.pushworker.service.PushWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumer group "push-worker-group" (PRD section 16). */
@Component
public class PushJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(PushJobConsumer.class);

    private final PushWorkerService pushWorkerService;
    private final ObjectMapper objectMapper;

    public PushJobConsumer(PushWorkerService pushWorkerService, ObjectMapper objectMapper) {
        this.pushWorkerService = pushWorkerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${notification.channels.push-topic}",
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
        pushWorkerService.handle(job);
    }
}
