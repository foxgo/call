package com.callcenter.search;

import com.callcenter.common.config.CallCommonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.callcenter")
@Import(CallCommonAutoConfiguration.class)
public class CallSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallSearchApplication.class, args);
    }
}
