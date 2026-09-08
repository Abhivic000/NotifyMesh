package com.notification.smsworker.retry;

import com.notification.smsworker.entity.RetryQueueEntry;
import com.notification.smsworker.repository.RetryQueueRepository;
import com.notification.smsworker.service.SmsWorkerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RetryPoller {

    private final RetryQueueRepository retryQueueRepository;
    private final SmsWorkerService smsWorkerService;

    public RetryPoller(RetryQueueRepository retryQueueRepository, SmsWorkerService smsWorkerService) {
        this.retryQueueRepository = retryQueueRepository;
        this.smsWorkerService = smsWorkerService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.poll-interval-ms:2000}")
    public void pollDueRetries() {
        List<RetryQueueEntry> due = retryQueueRepository.findByNextAttemptAtLessThanEqual(Instant.now());
        for (RetryQueueEntry entry : due) {
            smsWorkerService.retryDelivery(entry);
        }
    }
}
