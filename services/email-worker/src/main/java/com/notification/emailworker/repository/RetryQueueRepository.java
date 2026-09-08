package com.notification.emailworker.repository;

import com.notification.emailworker.entity.RetryQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetryQueueRepository extends JpaRepository<RetryQueueEntry, UUID> {

    List<RetryQueueEntry> findByNextAttemptAtLessThanEqual(Instant now);

    Optional<RetryQueueEntry> findByNotificationId(String notificationId);
}
