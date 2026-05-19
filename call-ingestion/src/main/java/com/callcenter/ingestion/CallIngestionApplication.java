package com.callcenter.ingestion;

import com.callcenter.common.config.CallCommonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = "com.callcenter")
@Import(CallCommonAutoConfiguration.class)
@EnableRetry
public class CallIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallIngestionApplication.class, args);
    }
}
