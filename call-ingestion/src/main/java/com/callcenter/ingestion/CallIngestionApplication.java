package com.callcenter.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.callcenter")
@EnableScheduling
public class CallIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallIngestionApplication.class, args);
    }
}
