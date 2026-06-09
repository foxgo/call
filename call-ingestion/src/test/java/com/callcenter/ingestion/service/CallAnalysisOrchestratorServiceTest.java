package com.callcenter.ingestion.service;

import com.callcenter.ingestion.postprocess.OutboxEventFactory;
import com.callcenter.ingestion.service.CallAnalysisGateway;
import com.callcenter.ingestion.service.OutboxEventRepository;
import com.callcenter.ingestion.service.RoundRepository;
import com.callcenter.ingestion.model.CallAnalysisRequest;
import com.callcenter.ingestion.model.CallAnalysisResponse;
import com.callcenter.ingestion.model.DomainEventMessage;
import com.callcenter.ingestion.model.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.model.CallRecordPersistedEvent;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRoundData;
import com.callcenter.ingestion.model.OutboxEventData;
import com.callcenter.ingestion.config.PostprocessProperties;
import com.callcenter.ingestion.testsupport.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CallAnalysisOrchestratorServiceTest {

    @Test
    void shouldSaveSucceededResultAndCreateCompletionOutboxWhenLlmEnabled() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallAnalysisGateway analysisGateway = mock(CallAnalysisGateway.class);
        CallAnalysisResultService resultService = mock(CallAnalysisResultService.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        OutboxEventData outboxEvent = outboxEvent();
        PostprocessPropertiesBuilder propertiesBuilder = new PostprocessPropertiesBuilder(true);
        CallAnalysisOrchestratorService service = new CallAnalysisOrchestratorService(
                objectMapper,
                callRoundRepository,
                analysisGateway,
                resultService,
                outboxRepository,
                outboxEventFactory,
                propertiesBuilder.build()
        );
        DomainEventMessage event = persistedEvent(objectMapper, recordData());
        List<CallRoundData> rounds = List.of(roundData());

        when(callRoundRepository.findByCallId(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0))).thenReturn(rounds);
        when(analysisGateway.analyze(any(CallAnalysisRequest.class)))
                .thenReturn(new CallAnalysisResponse(List.of("RISK"), true, 0.92f, "v1"));
        when(outboxEventFactory.analysisCompleted(any(CallAnalysisCompletedEvent.class))).thenReturn(outboxEvent);

        service.handlePersistedEvent(event);

        ArgumentCaptor<CallAnalysisRequest> requestCaptor = ArgumentCaptor.forClass(CallAnalysisRequest.class);
        verify(analysisGateway).analyze(requestCaptor.capture());
        CallAnalysisRequest request = requestCaptor.getValue();
        assertThat(request.record().callId()).isEqualTo(1001L);
        assertThat(request.rounds()).hasSize(1);
        verify(resultService).saveSucceeded(9L, 1001L, List.of("RISK"), true, 0.92f, "v1");
        verify(outboxRepository).saveAll(List.of(outboxEvent));
    }

    @Test
    void shouldCreateCompletionOutboxWithoutCallingLlmWhenDisabled() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallAnalysisGateway analysisGateway = mock(CallAnalysisGateway.class);
        CallAnalysisResultService resultService = mock(CallAnalysisResultService.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        OutboxEventData outboxEvent = outboxEvent();
        CallAnalysisOrchestratorService service = new CallAnalysisOrchestratorService(
                objectMapper,
                callRoundRepository,
                analysisGateway,
                resultService,
                outboxRepository,
                outboxEventFactory,
                new PostprocessPropertiesBuilder(false).build()
        );
        DomainEventMessage event = persistedEvent(objectMapper, recordData());

        when(outboxEventFactory.analysisCompleted(any(CallAnalysisCompletedEvent.class))).thenReturn(outboxEvent);

        service.handlePersistedEvent(event);

        verifyNoInteractions(analysisGateway, callRoundRepository);
        verify(resultService).findByTenantIdAndCallId(9L, 1001L);
        verifyNoMoreInteractions(resultService);
        verify(outboxRepository).saveAll(List.of(outboxEvent));
    }

    @Test
    void shouldSaveDegradedResultAndCreateCompletionOutboxAfterRetryExhausted() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallAnalysisGateway analysisGateway = mock(CallAnalysisGateway.class);
        CallAnalysisResultService resultService = mock(CallAnalysisResultService.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        OutboxEventData outboxEvent = outboxEvent();
        CallAnalysisOrchestratorService service = new CallAnalysisOrchestratorService(
                objectMapper,
                callRoundRepository,
                analysisGateway,
                resultService,
                outboxRepository,
                outboxEventFactory,
                new PostprocessPropertiesBuilder(true).build()
        );
        DomainEventMessage event = persistedEvent(objectMapper, recordData());

        when(callRoundRepository.findByCallId(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0)))
                .thenReturn(List.of(roundData()));
        when(outboxEventFactory.analysisCompleted(any(CallAnalysisCompletedEvent.class))).thenReturn(outboxEvent);
        when(analysisGateway.analyze(any(CallAnalysisRequest.class))).thenThrow(new IllegalStateException("llm timeout"));

        service.handlePersistedEvent(event, 3, 3);

        verify(resultService).saveDegraded(9L, 1001L, "java.lang.IllegalStateException: llm timeout");
        verify(outboxRepository).saveAll(List.of(outboxEvent));
    }

    private static DomainEventMessage persistedEvent(ObjectMapper objectMapper, CallRecordData record) {
        return new DomainEventMessage(
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(CallRecordPersistedEvent.from(record))
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

    private static OutboxEventData outboxEvent() {
        return new OutboxEventData(
                null,
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
                "CALL_RECORD",
                "1001",
                9L,
                "1001",
                1,
                "{\"eventType\":\"call_record_analysis_completed\"}",
                "NEW",
                0,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 10, 4),
                LocalDateTime.of(2026, 5, 20, 10, 4)
        );
    }

    private static final class PostprocessPropertiesBuilder {

        private final boolean llmEnabled;

        private PostprocessPropertiesBuilder(boolean llmEnabled) {
            this.llmEnabled = llmEnabled;
        }

        private PostprocessProperties build() {
            PostprocessProperties properties = new PostprocessProperties();
            properties.setLlmEnabled(llmEnabled);
            return properties;
        }
    }
}
