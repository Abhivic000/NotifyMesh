package com.notification.observability.correlation;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads {@code X-Correlation-ID} from the incoming request, generating one if the
 * client didn't send it (PRD section 52), puts it in SLF4J's MDC so every log line
 * for this request can include it, and echoes it back on the response.
 * <p>
 * Deliberately NOT annotated {@code @Component}: this class lives in a shared library
 * consumed by services with different base packages, so relying on Spring's classpath
 * component scan to find it here would be fragile. Each service explicitly registers
 * this as a {@code @Bean} instead - see event-ingestion-service's ObservabilityConfig.
 */
public class CorrelationIdFilter implements Filter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, correlationId);
            httpResponse.setHeader(HEADER_NAME, correlationId);
            chain.doFilter(request, response);
        } finally {
            // Servlet containers reuse threads across requests - an uncleared MDC
            // value would leak this request's correlation ID into a later, unrelated
            // request's logs on the same thread.
            MDC.remove(MDC_KEY);
        }
    }
}
