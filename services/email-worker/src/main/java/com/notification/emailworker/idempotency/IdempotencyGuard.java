package com.notification.emailworker.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis fast-path duplicate detection (PRD section 20, Layer 2) - claims a
 * notificationId via atomic SET NX EX before any processing happens. If Redis itself
 * is unavailable, this fails OPEN (treats the claim as successful) rather than
 * blocking delivery entirely - Redis is an optimization, never the source of truth
 * (PRD section 67: a Redis outage must not silently break the system, but it also
 * must not be allowed to halt it). The real backstop against duplicate *processing*
 * is that this whole pipeline is designed to be safely reprocessable regardless.
 */
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

    /** @return true if this call successfully claimed the notification (proceed with processing). */
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

    /**
     * Used only by an admin-initiated DLQ retry (PRD section 34). The original claim
     * from before this notification reached the DLQ is still held (up to the 24h TTL)
     * - without clearing it first, republishing would be silently swallowed by
     * {@link #tryClaim} as an apparent duplicate, and the retry would never actually
     * run.
     */
    public void clearClaim(String notificationId) {
        try {
            redisTemplate.delete(KEY_PREFIX + notificationId);
        } catch (Exception redisUnavailable) {
            log.warn("Redis unavailable while clearing idempotency claim for {}: {}",
                    notificationId, redisUnavailable.getMessage());
        }
    }
}
