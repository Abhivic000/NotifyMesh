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

    public MockEmailProvider(@Value("${notification.provider.email.failure-rate:0.1}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public ProviderResult send(String recipient, String subject, String renderedBody) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            FailureMode mode = FAILURE_MODES.get(ThreadLocalRandom.current().nextInt(FAILURE_MODES.size()));
            log.debug("Mock provider simulating failure: {}", mode.errorCode());
            return ProviderResult.failure(mode.errorCode(), mode.retryable(),
                    "Simulated failure: " + mode.errorCode());
        }

        String providerMessageId = "mock-" + UUID.randomUUID();
        return ProviderResult.success(providerMessageId, "202 Accepted (simulated)");
    }
}
