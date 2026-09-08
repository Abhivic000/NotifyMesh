package com.notification.emailworker.provider;

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

/**
 * Wraps the real provider call with a circuit breaker (PRD section 35) and a bounded
 * timeout (PRD section 36). The subtlety: Resilience4j's CircuitBreaker only counts
 * THROWN exceptions as failures by default - our provider never throws, it always
 * returns a ProviderResult, even for a simulated HTTP 500. Left as-is, the circuit
 * breaker would never see a failure and would never open, no matter how badly the
 * provider is "failing." Fixed by explicitly throwing when the result represents a
 * failure, so the circuit breaker actually observes it, then unwrapping that back
 * into the original ProviderResult in the catch block below.
 */
@Component
public class ProtectedEmailProviderCaller {

    private final NotificationProvider provider;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService providerCallExecutor;
    private final long timeoutMs;

    public ProtectedEmailProviderCaller(NotificationProvider provider,
                                         CircuitBreakerRegistry circuitBreakerRegistry,
                                         ExecutorService providerCallExecutor,
                                         @Value("${notification.provider.timeout-ms:3000}") long timeoutMs) {
        this.provider = provider;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("emailProvider");
        this.providerCallExecutor = providerCallExecutor;
        this.timeoutMs = timeoutMs;
    }

    public NotificationProvider.ProviderResult call(String recipient, String subject, String body) {
        Callable<NotificationProvider.ProviderResult> protectedCall =
                CircuitBreaker.decorateCallable(circuitBreaker, () -> callWithTimeout(recipient, subject, body));
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

    private NotificationProvider.ProviderResult callWithTimeout(String recipient, String subject, String body)
            throws Exception {
        Future<NotificationProvider.ProviderResult> future =
                providerCallExecutor.submit(() -> provider.send(recipient, subject, body));

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
            // Deliberately thrown, not returned - see the class Javadoc.
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
