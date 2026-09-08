package com.notification.smsworker.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Same design as email-worker's ProtectedEmailProviderCaller - see its Javadoc. */
@Component
public class ProtectedSmsProviderCaller {

    private final NotificationProvider provider;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService providerCallExecutor;
    private final long timeoutMs;

    public ProtectedSmsProviderCaller(NotificationProvider provider,
                                       CircuitBreakerRegistry circuitBreakerRegistry,
                                       ExecutorService providerCallExecutor,
                                       @Value("${notification.provider.timeout-ms:3000}") long timeoutMs) {
        this.provider = provider;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("smsProvider");
        this.providerCallExecutor = providerCallExecutor;
        this.timeoutMs = timeoutMs;
    }

    public NotificationProvider.ProviderResult call(String recipient, String body) {
        Callable<NotificationProvider.ProviderResult> protectedCall =
                CircuitBreaker.decorateCallable(circuitBreaker, () -> callWithTimeout(recipient, body));
        try {
            return protectedCall.call();
        } catch (CallNotPermittedException circuitOpen) {
            return NotificationProvider.ProviderResult.failure(
                    "CIRCUIT_OPEN", true, "Circuit breaker open - provider calls suspended");
        } catch (ProviderFailureException providerFailure) {
            return providerFailure.result;
        } catch (TimeoutException timeout) {
            return NotificationProvider.ProviderResult.failure(
                    "PROVIDER_TIMEOUT", true, "Provider call exceeded " + timeoutMs + "ms");
        } catch (Exception unexpected) {
            return NotificationProvider.ProviderResult.failure("PROVIDER_ERROR", true, unexpected.getMessage());
        }
    }

    private NotificationProvider.ProviderResult callWithTimeout(String recipient, String body) throws Exception {
        Future<NotificationProvider.ProviderResult> future =
                providerCallExecutor.submit(() -> provider.send(recipient, body));

        NotificationProvider.ProviderResult result;
        try {
            result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timedOut) {
            future.cancel(true);
            throw timedOut;
        } catch (ExecutionException wrapped) {
            if (wrapped.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw wrapped;
        }

        if (!result.success()) {
            throw new ProviderFailureException(result);
        }
        return result;
    }

    private static final class ProviderFailureException extends RuntimeException {
        private final NotificationProvider.ProviderResult result;

        ProviderFailureException(NotificationProvider.ProviderResult result) {
            super(result.errorCode());
            this.result = result;
        }
    }
}
