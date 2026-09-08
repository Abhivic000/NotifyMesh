package com.notification.pushworker.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockPushProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPushProvider.class);

    private record FailureMode(String errorCode, boolean retryable) {
    }

    private static final List<FailureMode> FAILURE_MODES = List.of(
            new FailureMode("PROVIDER_TIMEOUT", true),
            new FailureMode("PROVIDER_HTTP_500", true),
            new FailureMode("PROVIDER_HTTP_429", true),
            new FailureMode("INVALID_DEVICE_TOKEN", false)
    );

    private final double failureRate;

    public MockPushProvider(@Value("${notification.provider.push.failure-rate:0.1}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public ProviderResult send(String recipient, String renderedBody) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            FailureMode mode = FAILURE_MODES.get(ThreadLocalRandom.current().nextInt(FAILURE_MODES.size()));
            log.debug("Mock push provider simulating failure: {}", mode.errorCode());
            return ProviderResult.failure(mode.errorCode(), mode.retryable(),
                    "Simulated failure: " + mode.errorCode());
        }
        return ProviderResult.success("mock-push-" + UUID.randomUUID(), "202 Accepted (simulated)");
    }
}
