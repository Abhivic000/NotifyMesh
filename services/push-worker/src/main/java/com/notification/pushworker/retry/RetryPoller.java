package com.notification.pushworker.retry;

import com.notification.pushworker.entity.RetryQueueEntry;
import com.notification.pushworker.repository.RetryQueueRepository;
import com.notification.pushworker.service.PushWorkerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RetryPoller {

    private final RetryQueueRepository retryQueueRepository;
    private final PushWorkerService pushWorkerService;

    public RetryPoller(RetryQueueRepository retryQueueRepository, PushWorkerService pushWorkerService) {
        this.retryQueueRepository = retryQueueRepository;
        this.pushWorkerService = pushWorkerService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.poll-interval-ms:2000}")
    public void pollDueRetries() {
        List<RetryQueueEntry> due = retryQueueRepository.findByNextAttemptAtLessThanEqual(Instant.now());
        for (RetryQueueEntry entry : due) {
            pushWorkerService.retryDelivery(entry);
        }
    }
}
