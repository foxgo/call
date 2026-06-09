package com.callcenter.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @Test
    void shouldPropagateMdcValuesIntoDecoratedTask() {
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();

        MDC.put(StructuredLogFields.REQUEST_ID, "req-1");
        MDC.put(StructuredLogFields.TRACE_ID, "trace-1");
        try {
            Runnable decorated = decorator.decorate(() -> {
                requestId.set(MDC.get(StructuredLogFields.REQUEST_ID));
                traceId.set(MDC.get(StructuredLogFields.TRACE_ID));
            });

            MDC.clear();
            decorated.run();

            assertThat(requestId).hasValue("req-1");
            assertThat(traceId).hasValue("trace-1");
        } finally {
            MDC.clear();
        }
    }
}
