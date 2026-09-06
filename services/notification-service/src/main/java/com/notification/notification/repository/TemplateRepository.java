package com.notification.notification.repository;

import com.notification.notification.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    // One row per (templateKey, channel) is what "applicable channels for this event
    // type" means in this design - see NotificationProcessingService.
    List<Template> findByTemplateKeyAndActiveTrue(String templateKey);
}
