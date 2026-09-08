package com.notification.smsworker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ProviderExecutorConfig {

    @Bean
    public ExecutorService providerCallExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
