package com.notification.emailworker.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Local environments use mock providers (PRD section 70) - simulates the failure
 * modes a real provider actually produces, so the reliability behavior (Phase 6) can
 * be demonstrated without a paid email service. EMAIL_PROVIDER_FAILURE_RATE controls
 * how often ANY failure occurs; when one does, it's a mix of transient and permanent
 * failure types, matching PRD section 33's retryable/non-retryable distinction.
 */
@Component
public class MockEmailProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockEmailProvider.class);

    private record FailureMode(String errorCode, boolean retryable) {
    }

    private static final List<FailureMode> FAILURE_MODES = List.of(
            new FailureMode("PROVIDER_TIMEOUT", true),
            new FailureMode("PROVIDER_HTTP_500", true),
            new FailureMode("PROVIDER_HTTP_429", true),
            new FailureMode("INVALID_RECIPIENT", false)
    );

    private final double failureRate;
    private final long simulatedTimeoutLatencyMs;

    public MockEmailProvider(@Value("${notification.provider.email.failure-rate:0.1}") double failureRate,
                              @Value("${notification.provider.simulated-timeout-latency-ms:5000}") long simulatedTimeoutLatencyMs) {
        this.failureRate = failureRate;
        this.simulatedTimeoutLatencyMs = simulatedTimeoutLatencyMs;
    }

    @Override
    public ProviderResult send(String recipient, String subject, String renderedBody) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            FailureMode mode = FAILURE_MODES.get(ThreadLocalRandom.current().nextInt(FAILURE_MODES.size()));
            log.debug("Mock provider simulating failure: {}", mode.errorCode());

            // PROVIDER_TIMEOUT actually takes real time to "fail" - a genuine hang, not
            // just a label - so the caller's timeout wrapper has something real to catch.
            if ("PROVIDER_TIMEOUT".equals(mode.errorCode())) {
                sleepUninterruptibly(simulatedTimeoutLatencyMs);
            }
            return ProviderResult.failure(mode.errorCode(), mode.retryable(),
                    "Simulated failure: " + mode.errorCode());
        }

        String providerMessageId = "mock-" + UUID.randomUUID();
        return ProviderResult.success(providerMessageId, "202 Accepted (simulated)");
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
