package com.callcenter.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.callcenter")
public class CallIamApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallIamApplication.class, args);
    }
}
