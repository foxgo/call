package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.CallRoundMessage;
import com.callcenter.ingestion.model.InboundMessage;
import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.service.CallRoundIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqCallRoundConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDelegateRoundPayloadToRoundIngestionService() throws Exception {
        CallRoundIngestionService ingestionService = mock(CallRoundIngestionService.class);
        RocketMqCallRoundConsumer consumer = new RocketMqCallRoundConsumer(objectMapper, ingestionService);
        CallRoundMessage round = new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hello", "GREETING", 1L);

        when(ingestionService.process(any())).thenReturn(true);

        consumer.onMessage(message(objectMapper.writeValueAsString(round)));

        ArgumentCaptor<InboundMessage<CallRoundMessage>> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(ingestionService).process(captor.capture());
        InboundMessage<CallRoundMessage> inbound = captor.getValue();
        assertEquals("9", inbound.messageKey());
        assertEquals(MessageType.ROUND, inbound.messageType());
        assertEquals("1001:77", inbound.idempotencyKey());
        assertEquals(round, inbound.payload());
    }

    @Test
    void shouldThrowWhenRetryableFailureNeedsRocketMqReconsume() throws Exception {
        CallRoundIngestionService ingestionService = mock(CallRoundIngestionService.class);
        RocketMqCallRoundConsumer consumer = new RocketMqCallRoundConsumer(objectMapper, ingestionService);
        CallRoundMessage round = new CallRoundMessage(77L, 9L, 1001L, 1, "AGENT", "hello", "GREETING", 1L);

        when(ingestionService.process(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message(objectMapper.writeValueAsString(round))));
    }

    private static MessageExt message(String payload) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }
}
