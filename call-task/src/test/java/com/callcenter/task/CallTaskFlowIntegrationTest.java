package com.callcenter.task;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires Maven dependency resolution and local infrastructure to execute")
@SpringBootTest(classes = CallTaskApplication.class)
class CallTaskFlowIntegrationTest {

    @Test
    void shouldRunTaskImportDispatchAndWritebackFlow() {
    }
}
