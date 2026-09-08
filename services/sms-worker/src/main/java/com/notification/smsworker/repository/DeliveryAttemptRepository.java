package com.notification.smsworker.repository;

import com.notification.smsworker.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
}
