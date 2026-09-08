package com.notification.pushworker.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Same narrow cross-schema exception as email-worker's - see the Phase 4 design write-up. */
@Component
public class NotificationServiceDataAccess {

    private final JdbcTemplate jdbcTemplate;

    public NotificationServiceDataAccess(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record TemplateView(String subject, String body) {
    }

    public TemplateView findTemplate(UUID templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT subject, body FROM notification.templates WHERE id = ?",
                (rs, rowNum) -> new TemplateView(rs.getString("subject"), rs.getString("body")),
                templateId
        );
    }

    public void markSent(UUID notificationId, String providerMessageId) {
        jdbcTemplate.update(
                "UPDATE notification.notifications SET status = 'SENT', sent_at = ?, provider_message_id = ? WHERE id = ?",
                Timestamp.from(Instant.now()), providerMessageId, notificationId
        );
    }

    public void markFailed(UUID notificationId) {
        jdbcTemplate.update(
                "UPDATE notification.notifications SET status = 'FAILED', failed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), notificationId
        );
    }

    public void markRetrying(UUID notificationId, int retryCount) {
        jdbcTemplate.update(
                "UPDATE notification.notifications SET status = 'RETRYING', retry_count = ? WHERE id = ?",
                retryCount, notificationId
        );
    }

    public void markDlq(UUID notificationId) {
        jdbcTemplate.update(
                "UPDATE notification.notifications SET status = 'DLQ', failed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), notificationId
        );
    }
}
