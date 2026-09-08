package com.notification.pushworker.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.pushworker.entity.AuditLog;
import com.notification.pushworker.entity.DlqItem;
import com.notification.pushworker.idempotency.IdempotencyGuard;
import com.notification.pushworker.repository.AuditLogRepository;
import com.notification.pushworker.repository.DlqItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DlqAdminService {

    private static final Logger log = LoggerFactory.getLogger(DlqAdminService.class);

    private final DlqItemRepository dlqItemRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdempotencyGuard idempotencyGuard;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String pushTopic;

    public DlqAdminService(DlqItemRepository dlqItemRepository,
                            AuditLogRepository auditLogRepository,
                            IdempotencyGuard idempotencyGuard,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper,
                            @Value("${notification.channels.push-topic}") String pushTopic) {
        this.dlqItemRepository = dlqItemRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyGuard = idempotencyGuard;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.pushTopic = pushTopic;
    }

    public Page<DlqItem> list(String status, Pageable pageable) {
        return status == null
                ? dlqItemRepository.findAll(pageable)
                : dlqItemRepository.findByStatus(status, pageable);
    }

    public DlqItem get(UUID id) {
        return dlqItemRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("DLQ item not found: " + id));
    }

    public void retry(UUID id, String actorId) {
        DlqItem item = get(id);
        idempotencyGuard.clearClaim(item.getNotificationId());

        String payload = serialize(item.getOriginalMessage());
        kafkaTemplate.send(pushTopic, item.getNotificationId(), payload);

        item.markRetried();
        dlqItemRepository.save(item);

        audit(actorId, "DLQ_RETRY", item.getId().toString());
        log.info("DLQ item {} republished to {} by {}", item.getId(), pushTopic, actorId);
    }

    public void discard(UUID id, String actorId) {
        DlqItem item = get(id);
        item.markDiscarded();
        dlqItemRepository.save(item);

        audit(actorId, "DLQ_DISCARD", item.getId().toString());
        log.info("DLQ item {} discarded by {}", item.getId(), actorId);
    }

    private void audit(String actorId, String action, String resourceId) {
        auditLogRepository.save(new AuditLog(actorId, action, "DLQ_ITEM", resourceId, null));
    }

    private String serialize(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize DLQ item's original message", e);
        }
    }
}
