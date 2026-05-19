package com.callcenter.ops;

import com.callcenter.common.config.CallCommonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = "com.callcenter")
@Import(CallCommonAutoConfiguration.class)
public class CallOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallOpsApplication.class, args);
    }
}
