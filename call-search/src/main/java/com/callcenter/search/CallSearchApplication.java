package com.callcenter.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.callcenter")
public class CallSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallSearchApplication.class, args);
    }
}
