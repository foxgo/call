package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.service.CallRecordIndexService;
import com.callcenter.ingestion.service.CallAnalysisResultService;
import com.callcenter.ingestion.service.CallRoundMysqlService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistedIndexConsumerTest {

    @Test
    void shouldDeserializeRecordPersistedEventAndIndexPayload() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        ElasticsearchBulkService bulkService = mock(ElasticsearchBulkService.class);
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRoundMysqlService callRoundMysqlService = mock(CallRoundMysqlService.class);
        CallAnalysisResultService callAnalysisResultService = mock(CallAnalysisResultService.class);
        CallRecordIndexService indexService = new CallRecordIndexService(
                objectMapper,
                bulkService,
                shardingRouter,
                callRoundMysqlService,
                callAnalysisResultService
        );
        DomainEventMessage event = new DomainEventMessage(
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
        );
        when(shardingRouter.routeRound(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0)))
                .thenReturn(new ShardKey(9L, 0, 3, "202605"));
        when(callRoundMysqlService.listByCallId(any(), anyLong()))
                .thenReturn(List.of(roundEntity()));
        when(callAnalysisResultService.findByTenantIdAndCallId(9L, 1001L))
                .thenReturn(analysisResultEntity());

        indexService.indexAnalysisCompletedEvent(event);

        verify(bulkService).bulkIndexRecords(any(List.class), any(java.util.Map.class));
        verify(bulkService).bulkIndexRounds(any(List.class));
        verify(callAnalysisResultService).findByTenantIdAndCallId(9L, 1001L);
    }

    @Test
    void shouldDispatchRecordEventToRecordIndexService() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        RocketMqElasticSearchConsumer consumer = new RocketMqElasticSearchConsumer(
                objectMapper,
                indexService
        );
        DomainEventMessage event = new DomainEventMessage(
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
        );

        MessageExt message = new MessageExt();
        message.setTopic("call_record_analysis_completed");
        message.setBody(objectMapper.writeValueAsBytes(event));

        consumer.onMessage(message);

        verify(indexService).indexAnalysisCompletedEvent(any(DomainEventMessage.class));
    }

    @Test
    void shouldRejectRecordPersistedEvent() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        RocketMqElasticSearchConsumer consumer = new RocketMqElasticSearchConsumer(
                objectMapper,
                indexService
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

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理 RocketMQ 落库事件失败");
    }

    @Test
    void shouldPropagateFailureWhenIndexingFails() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        RocketMqElasticSearchConsumer consumer = new RocketMqElasticSearchConsumer(
                objectMapper,
                indexService
        );
        DomainEventMessage event = new DomainEventMessage(
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
        );

        org.mockito.Mockito.doThrow(new IllegalStateException("es down"))
                .when(indexService)
                .indexAnalysisCompletedEvent(any(DomainEventMessage.class));

        MessageExt message = new MessageExt();
        message.setTopic("call_record_analysis_completed");
        message.setBody(objectMapper.writeValueAsBytes(event));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理 RocketMQ 落库事件失败");
    }

    private static CallAnalysisResultEntity analysisResultEntity() {
        CallAnalysisResultEntity entity = new CallAnalysisResultEntity();
        entity.setTenantId(9L);
        entity.setCallId(1001L);
        entity.setStatus("SUCCEEDED");
        entity.setTags("[\"RISK\"]");
        entity.setRiskFlag(true);
        entity.setQualityScore(0.92f);
        entity.setAiVersion("v1");
        entity.setCompletedAt(LocalDateTime.of(2026, 5, 20, 10, 5));
        return entity;
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
        entity.setRoundTotal(1);
        entity.setRecordingUrl("https://cdn.example.com/recordings/1001.mp3");
        entity.setErrorCode(1001);
        entity.setErrorDescription("callee busy");
        entity.setHangupBy((byte) 1);
        entity.setConnected((byte) 1);
        entity.setRingDuration(1500L);
        entity.setRingStartTime(LocalDateTime.of(2026, 5, 20, 10, 0, 1, 500_000_000));
        entity.setHangupTime(LocalDateTime.of(2026, 5, 20, 10, 3, 0, 250_000_000));
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
