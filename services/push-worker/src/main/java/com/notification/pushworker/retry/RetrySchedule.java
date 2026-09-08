package com.notification.pushworker.retry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

    public Instant nextAttemptAt(int completedAttemptNumber) {
        int delayIndex = completedAttemptNumber - 1;
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
