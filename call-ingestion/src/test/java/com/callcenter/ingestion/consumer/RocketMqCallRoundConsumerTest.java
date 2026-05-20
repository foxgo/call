package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.ingestion.service.CallRoundIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqCallRoundConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDelegateRoundEnvelopeToRoundIngestionService() throws Exception {
        CallRoundIngestionService ingestionService = mock(CallRoundIngestionService.class);
        RocketMqCallRoundConsumer consumer = new RocketMqCallRoundConsumer(objectMapper, ingestionService);
        DomainEventMessage envelope = new DomainEventMessage(
                "evt-2",
                "CALL_ROUND",
                "CALL",
                "1001",
                9L,
                Instant.parse("2026-05-20T08:00:00Z"),
                1,
                objectMapper.valueToTree(new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hello", "GREETING", 1L))
        );

        when(ingestionService.process(any())).thenReturn(true);

        consumer.onMessage(message(objectMapper.writeValueAsString(envelope)));

        verify(ingestionService).process(any());
    }

    @Test
    void shouldThrowWhenRetryableFailureNeedsRocketMqReconsume() throws Exception {
        CallRoundIngestionService ingestionService = mock(CallRoundIngestionService.class);
        RocketMqCallRoundConsumer consumer = new RocketMqCallRoundConsumer(objectMapper, ingestionService);
        DomainEventMessage envelope = new DomainEventMessage(
                "evt-2",
                "CALL_ROUND",
                "CALL",
                "1001",
                9L,
                Instant.parse("2026-05-20T08:00:00Z"),
                1,
                objectMapper.valueToTree(new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hello", "GREETING", 1L))
        );

        when(ingestionService.process(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message(objectMapper.writeValueAsString(envelope))));
    }

    private static MessageExt message(String payload) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }
}
