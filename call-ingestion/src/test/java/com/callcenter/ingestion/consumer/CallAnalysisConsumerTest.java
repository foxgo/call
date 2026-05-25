package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.ingestion.config.RocketMqProperties;
import com.callcenter.ingestion.service.CallAnalysisOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CallAnalysisConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDelegatePersistedEventWithRetryMetadata() throws Exception {
        CallAnalysisOrchestratorService service = mock(CallAnalysisOrchestratorService.class);
        RocketMqCallAnalysisConsumer consumer = new RocketMqCallAnalysisConsumer(objectMapper, service, rocketMqProperties(3));
        MessageExt message = message(persistedEvent());
        message.setReconsumeTimes(2);

        consumer.onMessage(message);

        verify(service).handlePersistedEvent(any(DomainEventMessage.class), eq(2), eq(3));
    }

    @Test
    void shouldThrowWhenAnalysisProcessingFailsBeforeRetryLimit() throws Exception {
        CallAnalysisOrchestratorService service = mock(CallAnalysisOrchestratorService.class);
        RocketMqCallAnalysisConsumer consumer = new RocketMqCallAnalysisConsumer(objectMapper, service, rocketMqProperties(3));
        MessageExt message = message(persistedEvent());

        doThrow(new IllegalStateException("llm error"))
                .when(service)
                .handlePersistedEvent(any(DomainEventMessage.class), eq(1), eq(3));
        message.setReconsumeTimes(1);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理 RocketMQ 分析事件失败");
    }

    private DomainEventMessage persistedEvent() {
        return new DomainEventMessage(
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.createObjectNode()
                        .put("callId", 1001L)
                        .put("tenantId", 9L)
                        .put("startTime", "2026-05-20T10:00:00")
        );
    }

    private static MessageExt message(DomainEventMessage event) throws Exception {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsBytes(event));
        return messageExt;
    }

    private static RocketMqProperties rocketMqProperties(int maxReconsumeTimes) {
        RocketMqProperties properties = new RocketMqProperties();
        properties.getConsumers().getAi().setMaxReconsumeTimes(maxReconsumeTimes);
        return properties;
    }
}
