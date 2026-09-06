package com.notification.event.repository;

import com.notification.event.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Pageable (not a hardcoded "findTop50By...") so the publisher's batch size stays
    // configuration-driven rather than baked into the method name.
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
