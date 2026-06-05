package com.callcenter.ingestion.inbound.postprocess;

import com.callcenter.ingestion.application.postprocess.ThirdPartyPushService;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ThirdPartyPushConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDispatchAnalysisCompletedEventToThirdPartyPushService() throws Exception {
        ThirdPartyPushService pushService = mock(ThirdPartyPushService.class);
        RocketMqThirdPartyPushConsumer consumer = new RocketMqThirdPartyPushConsumer(objectMapper, pushService);

        consumer.onMessage(message(event()));

        verify(pushService).pushAnalysisCompletedEvent(any(DomainEventMessage.class));
    }

    @Test
    void shouldPropagatePushFailureForRocketMqRetry() throws Exception {
        ThirdPartyPushService pushService = mock(ThirdPartyPushService.class);
        RocketMqThirdPartyPushConsumer consumer = new RocketMqThirdPartyPushConsumer(objectMapper, pushService);

        doThrow(new IllegalStateException("push failed"))
                .when(pushService)
                .pushAnalysisCompletedEvent(any(DomainEventMessage.class));

        assertThatThrownBy(() -> consumer.onMessage(message(event())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理 RocketMQ 第三方推送事件失败");
    }

    private DomainEventMessage event() {
        return new DomainEventMessage(
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
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

    private MessageExt message(DomainEventMessage event) throws Exception {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(objectMapper.writeValueAsBytes(event));
        return messageExt;
    }
}
