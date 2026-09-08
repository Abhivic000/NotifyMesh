package com.notification.pushworker.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.contract.dlq.DlqRecord;
import com.notification.pushworker.entity.DlqItem;
import com.notification.pushworker.repository.DlqItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final DlqItemRepository dlqItemRepository;
    private final ObjectMapper objectMapper;

    public DlqConsumer(DlqItemRepository dlqItemRepository, ObjectMapper objectMapper) {
        this.dlqItemRepository = dlqItemRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${notification.channels.push-dlq-topic}",
            groupId = "push-worker-dlq-group"
    )
    public void onDlqRecord(String rawMessage) {
        DlqRecord record;
        try {
            record = objectMapper.readValue(rawMessage, DlqRecord.class);
        } catch (Exception malformed) {
            log.error("Failed to deserialize DlqRecord, dropping: {}", rawMessage, malformed);
            return;
        }

        DlqItem item = new DlqItem(
                record.notificationId(), record.eventId(), record.channel(), record.originalMessage(),
                record.attemptCount(), record.lastError(), record.errorCode(),
                record.firstFailureAt(), record.lastFailureAt(), record.correlationId()
        );
        dlqItemRepository.save(item);
        log.info("Persisted DLQ item notificationId={} eventId={}", record.notificationId(), record.eventId());
    }
}
