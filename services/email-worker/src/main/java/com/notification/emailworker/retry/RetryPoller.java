package com.notification.emailworker.retry;

import com.notification.emailworker.entity.RetryQueueEntry;
import com.notification.emailworker.repository.RetryQueueRepository;
import com.notification.emailworker.service.EmailWorkerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Same shape as OutboxPublisher (Phase 2): poll for due work, act on it. Here, "due
 * work" is a retry_queue row whose next_attempt_at has arrived.
 */
@Component
public class RetryPoller {

    private final RetryQueueRepository retryQueueRepository;
    private final EmailWorkerService emailWorkerService;

    public RetryPoller(RetryQueueRepository retryQueueRepository, EmailWorkerService emailWorkerService) {
        this.retryQueueRepository = retryQueueRepository;
        this.emailWorkerService = emailWorkerService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.poll-interval-ms:2000}")
    public void pollDueRetries() {
        List<RetryQueueEntry> due = retryQueueRepository.findByNextAttemptAtLessThanEqual(Instant.now());
        for (RetryQueueEntry entry : due) {
            emailWorkerService.retryDelivery(entry);
        }
    }
}
