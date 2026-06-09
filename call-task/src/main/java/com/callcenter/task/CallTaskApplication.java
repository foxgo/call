package com.callcenter.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.callcenter")
@EnableScheduling
public class CallTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallTaskApplication.class, args);
    }
}
