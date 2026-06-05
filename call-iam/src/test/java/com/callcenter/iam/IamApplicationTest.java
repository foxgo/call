package com.callcenter.iam;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires full infrastructure wiring for datasource and Flyway beans")
@SpringBootTest(classes = IamApplication.class)
class IamApplicationTest {

    @Test
    void contextLoads() {
    }
}
