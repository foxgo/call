package com.callcenter.ingestion.inbound.consumer.postprocess;

import com.callcenter.ingestion.application.analysis.CallAnalysisResultService;
import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.application.port.SearchIndexGateway;
import com.callcenter.ingestion.application.postprocess.CallRecordIndexService;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.domain.event.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.domain.model.AnalysisResultData;
import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqRecordIndexConsumerTest {

    @Test
    void shouldDeserializeRecordPersistedEventAndIndexPayload() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        SearchIndexGateway searchIndexGateway = mock(SearchIndexGateway.class);
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallAnalysisResultService callAnalysisResultService = mock(CallAnalysisResultService.class);
        CallRecordIndexService indexService = new CallRecordIndexService(
                objectMapper,
                searchIndexGateway,
                callRoundRepository,
                callAnalysisResultService
        );
        DomainEventMessage event = analysisCompletedEvent(objectMapper);
        when(callRoundRepository.findByCallId(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0)))
                .thenReturn(List.of(roundData()));
        when(callAnalysisResultService.findByTenantIdAndCallId(9L, 1001L))
                .thenReturn(analysisResultData());

        indexService.indexAnalysisCompletedEvent(event);

        verify(searchIndexGateway).bulkIndexRecordData(any(List.class), any(List.class));
        verify(searchIndexGateway).bulkIndexRoundData(any(List.class));
        verify(callAnalysisResultService).findByTenantIdAndCallId(9L, 1001L);
    }

    @Test
    void shouldDispatchRecordEventToRecordIndexService() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        CallRecordIndexService indexService = mock(CallRecordIndexService.class);
        RocketMqRecordIndexConsumer consumer = new RocketMqRecordIndexConsumer(
                objectMapper,
                indexService
        );
        DomainEventMessage event = analysisCompletedEvent(objectMapper);

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
        RocketMqRecordIndexConsumer consumer = new RocketMqRecordIndexConsumer(
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
                objectMapper.valueToTree(recordData())
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
        RocketMqRecordIndexConsumer consumer = new RocketMqRecordIndexConsumer(
                objectMapper,
                indexService
        );
        DomainEventMessage event = analysisCompletedEvent(objectMapper);

        doThrow(new IllegalStateException("es down"))
                .when(indexService)
                .indexAnalysisCompletedEvent(any(DomainEventMessage.class));

        MessageExt message = new MessageExt();
        message.setTopic("call_record_analysis_completed");
        message.setBody(objectMapper.writeValueAsBytes(event));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理 RocketMQ 落库事件失败");
    }

    private static DomainEventMessage analysisCompletedEvent(ObjectMapper objectMapper) {
        return new DomainEventMessage(
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(CallAnalysisCompletedEvent.from(recordData()))
        );
    }

    private static AnalysisResultData analysisResultData() {
        return new AnalysisResultData(
                9L,
                1001L,
                "SUCCEEDED",
                "[\"RISK\"]",
                true,
                0.92f,
                "v1",
                null,
                LocalDateTime.of(2026, 5, 20, 10, 5)
        );
    }

    private static CallRecordData recordData() {
        return new CallRecordData(
                1001L,
                9L,
                1L,
                "13800138000",
                "021",
                2,
                180,
                1,
                "https://cdn.example.com/recordings/1001.mp3",
                1001,
                "callee busy",
                (byte) 1,
                (byte) 1,
                1500L,
                LocalDateTime.of(2026, 5, 20, 10, 0, 1, 500_000_000),
                LocalDateTime.of(2026, 5, 20, 10, 3, 0, 250_000_000),
                LocalDateTime.of(2026, 5, 20, 10, 0),
                LocalDateTime.of(2026, 5, 20, 10, 3),
                LocalDateTime.of(2026, 5, 20, 10, 4)
        );
    }

    private static CallRoundData roundData() {
        return new CallRoundData(
                77L,
                1001L,
                9L,
                1,
                "AGENT",
                "hello",
                "GREETING",
                LocalDateTime.of(2026, 5, 20, 10, 1),
                LocalDateTime.of(2026, 5, 20, 10, 1, 30)
        );
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
