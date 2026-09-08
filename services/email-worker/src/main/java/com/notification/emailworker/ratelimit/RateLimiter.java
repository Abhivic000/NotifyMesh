package com.notification.emailworker.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed fixed-window counter (PRD section 27): INCR a per-recipient-per-minute
 * key, EXPIRE it on first use, reject once the configured ceiling is hit. A true
 * sliding-window log (Redis ZSET of timestamps) would avoid the fixed-window's
 * boundary-burst edge case (2x the limit possible across a window boundary) at the
 * cost of more Redis operations per check - fixed-window is the documented,
 * deliberately simpler choice here.
 * <p>
 * Fails OPEN on Redis errors, same reasoning as IdempotencyGuard: Redis is an
 * optimization, never a hard dependency for whether delivery can proceed (PRD
 * section 67).
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String KEY_PREFIX = "rate:notification:";

    private final StringRedisTemplate redisTemplate;
    private final int limitPerMinute;

    public RateLimiter(StringRedisTemplate redisTemplate,
                        @Value("${notification.ratelimit.per-recipient-per-minute:100}") int limitPerMinute) {
        this.redisTemplate = redisTemplate;
        this.limitPerMinute = limitPerMinute;
    }

    public boolean isAllowed(String recipient) {
        try {
            String key = KEY_PREFIX + recipient;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            return count == null || count <= limitPerMinute;
        } catch (Exception redisUnavailable) {
            log.warn("Redis unavailable for rate limiting, allowing request through: {}",
                    redisUnavailable.getMessage());
            return true;
        }
    }
}
