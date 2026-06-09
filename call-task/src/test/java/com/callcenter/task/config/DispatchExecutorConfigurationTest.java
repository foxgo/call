package com.callcenter.task.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.callcenter.observability.logging.MdcTaskDecorator;
import com.callcenter.observability.logging.StructuredLogFields;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class DispatchExecutorConfigurationTest {

    @Test
    void shouldPropagateMdcIntoDispatchExecutors() throws InterruptedException {
        DispatchExecutorConfiguration configuration = new DispatchExecutorConfiguration();
        CallTaskDispatchProperties properties = new CallTaskDispatchProperties();
        properties.setDispatcherParallelism(1);
        properties.setDispatchSendParallelism(1);

        ThreadPoolTaskExecutor executor = configuration.callTaskDispatchExecutor(properties, new MdcTaskDecorator());
        AtomicReference<String> requestId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        MDC.put(StructuredLogFields.REQUEST_ID, "req-dispatch");
        try {
            executor.execute(() -> {
                requestId.set(MDC.get(StructuredLogFields.REQUEST_ID));
                latch.countDown();
            });
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(requestId).hasValue("req-dispatch");
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
