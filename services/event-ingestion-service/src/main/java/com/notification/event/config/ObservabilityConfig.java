package com.notification.event.config;

import com.notification.observability.correlation.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit bean registration for shared library components - see CorrelationIdFilter's
 * Javadoc for why this isn't just an @Component picked up by classpath scanning.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
