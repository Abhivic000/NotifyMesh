package com.notification.emailworker.retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The PRD's suggested retry schedule (section 32): attempt 1 is immediate (the first
 * Kafka-triggered send, not represented here), then delays of 5s/30s/2min/10min before
 * attempts 2-5. Not a constant multiplier (5->30 is x6, 30->120 is x4, 120->600 is x5)
 * so this is an explicit configurable list rather than an exponential formula -
 * matches the PRD's actual suggested numbers exactly instead of approximating them.
 * Jitter (+/- a configurable fraction) is applied on top, per PRD's explicit ask to
 * avoid synchronized retry storms.
 */
@Component
public class RetrySchedule {

    private final List<Long> delaysMs;
    private final int maxAttempts;
    private final double jitterFactor;

    public RetrySchedule(@Value("${notification.retry.delays-ms}") String delaysMsCsv,
                          @Value("${notification.retry.max-attempts:5}") int maxAttempts,
                          @Value("${notification.retry.jitter-factor:0.2}") double jitterFactor) {
        this.delaysMs = Arrays.stream(delaysMsCsv.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .toList();
        this.maxAttempts = maxAttempts;
        this.jitterFactor = jitterFactor;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** @param completedAttemptNumber the attempt that just failed (1-based). */
    public Instant nextAttemptAt(int completedAttemptNumber) {
        int delayIndex = completedAttemptNumber - 1; // attempt 1 failing schedules delaysMs[0], etc.
        long baseDelay = delayIndex < delaysMs.size()
                ? delaysMs.get(delayIndex)
                : delaysMs.get(delaysMs.size() - 1);
        long jittered = applyJitter(baseDelay);
        return Instant.now().plus(Duration.ofMillis(jittered));
    }

    private long applyJitter(long baseDelayMs) {
        double jitterRange = baseDelayMs * jitterFactor;
        double offset = ThreadLocalRandom.current().nextDouble(-jitterRange, jitterRange);
        return Math.max(0, Math.round(baseDelayMs + offset));
    }
}
