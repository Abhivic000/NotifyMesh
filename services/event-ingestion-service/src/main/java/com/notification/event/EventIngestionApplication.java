package com.notification.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventIngestionApplication.class, args);
    }
}
