package com.notification.emailworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailWorkerApplication.class, args);
    }
}
