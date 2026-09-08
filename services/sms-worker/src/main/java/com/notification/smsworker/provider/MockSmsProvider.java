package com.notification.smsworker.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MockSmsProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    private record FailureMode(String errorCode, boolean retryable) {
    }

    private static final List<FailureMode> FAILURE_MODES = List.of(
            new FailureMode("PROVIDER_TIMEOUT", true),
            new FailureMode("PROVIDER_HTTP_500", true),
            new FailureMode("PROVIDER_HTTP_429", true),
            new FailureMode("INVALID_PHONE_NUMBER", false)
    );

    private final double failureRate;
    private final long simulatedTimeoutLatencyMs;

    public MockSmsProvider(@Value("${notification.provider.sms.failure-rate:0.1}") double failureRate,
                            @Value("${notification.provider.simulated-timeout-latency-ms:5000}") long simulatedTimeoutLatencyMs) {
        this.failureRate = failureRate;
        this.simulatedTimeoutLatencyMs = simulatedTimeoutLatencyMs;
    }

    @Override
    public ProviderResult send(String recipient, String renderedBody) {
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            FailureMode mode = FAILURE_MODES.get(ThreadLocalRandom.current().nextInt(FAILURE_MODES.size()));
            log.debug("Mock SMS provider simulating failure: {}", mode.errorCode());
            if ("PROVIDER_TIMEOUT".equals(mode.errorCode())) {
                sleepUninterruptibly(simulatedTimeoutLatencyMs);
            }
            return ProviderResult.failure(mode.errorCode(), mode.retryable(),
                    "Simulated failure: " + mode.errorCode());
        }
        return ProviderResult.success("mock-sms-" + UUID.randomUUID(), "202 Accepted (simulated)");
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
