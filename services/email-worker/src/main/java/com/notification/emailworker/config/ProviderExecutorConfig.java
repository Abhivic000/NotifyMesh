package com.notification.emailworker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small shared thread pool the timeout wrapper runs provider calls on - separate
 * from Kafka's own consumer threads, so a hung provider call never blocks message
 * consumption itself, only the one job waiting on it.
 */
@Configuration
public class ProviderExecutorConfig {

    @Bean
    public ExecutorService providerCallExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
