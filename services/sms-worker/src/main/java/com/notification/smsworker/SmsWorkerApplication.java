package com.notification.smsworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmsWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsWorkerApplication.class, args);
    }
}
