package com.callcenter.task;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires full infrastructure wiring for MyBatis and datasource beans")
@SpringBootTest(classes = CallTaskApplication.class)
class CallTaskApplicationTest {

    @Test
    void contextLoads() {
    }
}
