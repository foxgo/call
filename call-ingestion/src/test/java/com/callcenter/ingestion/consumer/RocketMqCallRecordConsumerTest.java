package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.ingestion.model.InboundMessage;
import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.service.CallRecordIngestionService;
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

class RocketMqCallRecordConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldDelegateRecordPayloadToRecordIngestionService() throws Exception {
        CallRecordIngestionService ingestionService = mock(CallRecordIngestionService.class);
        RocketMqCallRecordConsumer consumer = new RocketMqCallRecordConsumer(objectMapper, ingestionService);
        CallRecordMessage record = new CallRecordMessage(1001L, 9L, 1L, "13800138000", "021", 1, 1L, 2L, 3, 2, null);

        when(ingestionService.process(any())).thenReturn(true);

        consumer.onMessage(message(objectMapper.writeValueAsString(record)));

        ArgumentCaptor<InboundMessage<CallRecordMessage>> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(ingestionService).process(captor.capture());
        InboundMessage<CallRecordMessage> inbound = captor.getValue();
        assertEquals("9", inbound.messageKey());
        assertEquals(MessageType.RECORD, inbound.messageType());
        assertEquals("1001", inbound.idempotencyKey());
        assertEquals(record, inbound.payload());
    }

    @Test
    void shouldThrowWhenRetryableFailureNeedsRocketMqReconsume() throws Exception {
        CallRecordIngestionService ingestionService = mock(CallRecordIngestionService.class);
        RocketMqCallRecordConsumer consumer = new RocketMqCallRecordConsumer(objectMapper, ingestionService);
        CallRecordMessage record = new CallRecordMessage(1001L, 9L, 1L, "13800138000", "021", 1, 1L, 2L, 3, 2, null);

        when(ingestionService.process(any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> consumer.onMessage(message(objectMapper.writeValueAsString(record))));
    }

    private static MessageExt message(String payload) {
        MessageExt messageExt = new MessageExt();
        messageExt.setBody(payload.getBytes(StandardCharsets.UTF_8));
        return messageExt;
    }
}
