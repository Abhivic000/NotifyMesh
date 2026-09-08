package com.notification.smsworker.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);
    private static final String KEY_PREFIX = "idempotency:notification:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public IdempotencyGuard(StringRedisTemplate redisTemplate,
                             @Value("${notification.idempotency.ttl-hours:24}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public boolean tryClaim(String notificationId) {
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + notificationId, "1", ttl);
            return Boolean.TRUE.equals(claimed);
        } catch (Exception redisUnavailable) {
            log.warn("Redis unavailable for idempotency check, proceeding without fast-path guard: {}",
                    redisUnavailable.getMessage());
            return true;
        }
    }

    /** Used only by an admin-initiated DLQ retry - see email-worker's IdempotencyGuard Javadoc. */
    public void clearClaim(String notificationId) {
        try {
            redisTemplate.delete(KEY_PREFIX + notificationId);
        } catch (Exception redisUnavailable) {
            log.warn("Redis unavailable while clearing idempotency claim for {}: {}",
                    notificationId, redisUnavailable.getMessage());
        }
    }
}
