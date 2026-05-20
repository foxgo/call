package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.ingestion.service.CallRecordIndexService;
import com.callcenter.ingestion.service.CallRoundIndexService;
import com.callcenter.ingestion.service.ElasticsearchBulkService;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PersistedIndexConsumerTest {

    @Test
    void shouldDeserializeRecordPersistedEventAndIndexPayload() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        ElasticsearchBulkService bulkService = mock(ElasticsearchBulkService.class);
        CallRecordIndexService indexService = new CallRecordIndexService(objectMapper, bulkService);
        DomainEventMessage event = new DomainEventMessage(
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
        );

        indexService.indexPersistedEvent(event);

        verify(bulkService).bulkIndexRecords(any(List.class));
    }

    @Test
    void shouldDeserializeRoundPersistedEventAndIndexPayload() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        ElasticsearchBulkService bulkService = mock(ElasticsearchBulkService.class);
        CallRoundIndexService indexService = new CallRoundIndexService(objectMapper, bulkService);
        DomainEventMessage event = new DomainEventMessage(
                "call_round_persisted:9:1001:77",
                "call_round_persisted",
                "CALL_ROUND",
                "77",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(roundEntity())
        );

        indexService.indexPersistedEvent(event);

        verify(bulkService).bulkIndexRounds(any(List.class));
    }

    @Test
    void shouldDispatchRecordEventToRecordIndexService() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        CallRoundIndexService roundIndexService = mock(CallRoundIndexService.class);
        RocketMqPersistedEventConsumer consumer = new RocketMqPersistedEventConsumer(
                objectMapper,
                indexService,
                roundIndexService,
                new com.callcenter.ingestion.config.PostprocessProperties()
        );
        DomainEventMessage event = new DomainEventMessage(
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
        );

        MessageExt message = new MessageExt();
        message.setTopic("call_record_persisted");
        message.setBody(objectMapper.writeValueAsBytes(event));

        consumer.onMessage(message);

        verify(indexService).indexPersistedEvent(any(DomainEventMessage.class));
    }

    @Test
    void shouldDispatchRoundEventToRoundIndexService() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        CallRoundIndexService roundIndexService = mock(CallRoundIndexService.class);
        RocketMqPersistedEventConsumer consumer = new RocketMqPersistedEventConsumer(
                objectMapper,
                indexService,
                roundIndexService,
                new com.callcenter.ingestion.config.PostprocessProperties()
        );
        DomainEventMessage event = new DomainEventMessage(
                "call_round_persisted:9:1001:77",
                "call_round_persisted",
                "CALL_ROUND",
                "77",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(roundEntity())
        );

        MessageExt message = new MessageExt();
        message.setTopic("call_round_persisted");
        message.setBody(objectMapper.writeValueAsBytes(event));

        consumer.onMessage(message);

        verify(roundIndexService).indexPersistedEvent(any(DomainEventMessage.class));
    }

    @Test
    void shouldPropagateFailureWhenIndexingFails() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        CallRoundIndexService roundIndexService = mock(CallRoundIndexService.class);
        RocketMqPersistedEventConsumer consumer = new RocketMqPersistedEventConsumer(
                objectMapper,
                indexService,
                roundIndexService,
                new com.callcenter.ingestion.config.PostprocessProperties()
        );
        DomainEventMessage event = new DomainEventMessage(
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
        );

        org.mockito.Mockito.doThrow(new IllegalStateException("es down"))
                .when(indexService)
                .indexPersistedEvent(any(DomainEventMessage.class));

        MessageExt message = new MessageExt();
        message.setTopic("call_record_persisted");
        message.setBody(objectMapper.writeValueAsBytes(event));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理 RocketMQ 落库事件失败");
    }

    private static CallRecordEntity recordEntity() {
        CallRecordEntity entity = new CallRecordEntity();
        entity.setCallId(1001L);
        entity.setTenantId(9L);
        entity.setTaskId(1L);
        entity.setPhone("13800138000");
        entity.setLineNumber("021");
        entity.setCallStatus(2);
        entity.setDuration(180);
        entity.setStartTime(LocalDateTime.of(2026, 5, 20, 10, 0));
        entity.setEndTime(LocalDateTime.of(2026, 5, 20, 10, 3));
        entity.setCreatedAt(LocalDateTime.of(2026, 5, 20, 10, 4));
        return entity;
    }

    private static CallRoundEntity roundEntity() {
        CallRoundEntity entity = new CallRoundEntity();
        entity.setRoundId(77L);
        entity.setCallId(1001L);
        entity.setTenantId(9L);
        entity.setRoundIndex(1);
        entity.setSpeaker("AGENT");
        entity.setContent("hello");
        entity.setIntent("GREETING");
        entity.setStartTime(LocalDateTime.of(2026, 5, 20, 10, 1));
        entity.setCreatedAt(LocalDateTime.of(2026, 5, 20, 10, 1, 30));
        return entity;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
