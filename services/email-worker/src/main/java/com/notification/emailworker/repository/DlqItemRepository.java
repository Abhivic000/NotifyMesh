package com.notification.emailworker.repository;

import com.notification.emailworker.entity.DlqItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DlqItemRepository extends JpaRepository<DlqItem, UUID> {

    Page<DlqItem> findByStatus(String status, Pageable pageable);
}
