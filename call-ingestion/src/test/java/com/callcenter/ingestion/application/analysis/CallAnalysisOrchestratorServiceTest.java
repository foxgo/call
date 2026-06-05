package com.callcenter.ingestion.application.analysis;

import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.domain.analysis.CallAnalysisRequest;
import com.callcenter.ingestion.domain.analysis.CallAnalysisResponse;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.application.outbox.OutboxEventFactory;
import com.callcenter.ingestion.infrastructure.config.PostprocessProperties;
import com.callcenter.ingestion.infrastructure.outbox.persistence.CallEventOutboxEntity;
import com.callcenter.ingestion.infrastructure.outbox.persistence.CallEventOutboxMapper;
import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.round.persistence.CallRoundEntity;
import com.callcenter.ingestion.infrastructure.analysis.client.CallAnalysisClient;
import com.callcenter.ingestion.infrastructure.round.persistence.MybatisCallRoundRepository;
import com.callcenter.ingestion.testsupport.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CallAnalysisOrchestratorServiceTest {

    @Test
    void shouldSaveSucceededResultAndCreateCompletionOutboxWhenLlmEnabled() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallAnalysisClient analysisClient = mock(CallAnalysisClient.class);
        CallAnalysisResultService resultService = mock(CallAnalysisResultService.class);
        CallEventOutboxMapper outboxMapper = mock(CallEventOutboxMapper.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        CallEventOutboxEntity outboxEvent = new CallEventOutboxEntity();
        PostprocessPropertiesBuilder propertiesBuilder = new PostprocessPropertiesBuilder(true);
        CallAnalysisOrchestratorService service = new CallAnalysisOrchestratorService(
                objectMapper,
                shardingRouter,
                callRoundRepository,
                analysisClient,
                resultService,
                outboxMapper,
                outboxEventFactory,
                propertiesBuilder.build()
        );
        DomainEventMessage event = persistedEvent(objectMapper, recordEntity());
        ShardKey shardKey = new ShardKey(9L, 0, 3, "202605");
        List<CallRoundEntity> rounds = List.of(roundEntity());

        when(shardingRouter.routeRound(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0))).thenReturn(shardKey);
        when(callRoundRepository.listByCallId(shardKey, 1001L)).thenReturn(rounds);
        when(analysisClient.analyze(any(CallAnalysisRequest.class)))
                .thenReturn(new CallAnalysisResponse(List.of("RISK"), true, 0.92f, "v1"));
        when(outboxEventFactory.analysisCompleted(any(CallRecordEntity.class))).thenReturn(outboxEvent);

        service.handlePersistedEvent(event);

        ArgumentCaptor<CallAnalysisRequest> requestCaptor = ArgumentCaptor.forClass(CallAnalysisRequest.class);
        verify(analysisClient).analyze(requestCaptor.capture());
        CallAnalysisRequest request = requestCaptor.getValue();
        assertThat(request.record().getCallId()).isEqualTo(1001L);
        assertThat(request.rounds()).hasSize(1);
        verify(resultService).saveSucceeded(9L, 1001L, List.of("RISK"), true, 0.92f, "v1");
        verify(outboxMapper).batchInsert(List.of(outboxEvent));
    }

    @Test
    void shouldCreateCompletionOutboxWithoutCallingLlmWhenDisabled() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallAnalysisClient analysisClient = mock(CallAnalysisClient.class);
        CallAnalysisResultService resultService = mock(CallAnalysisResultService.class);
        CallEventOutboxMapper outboxMapper = mock(CallEventOutboxMapper.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        CallEventOutboxEntity outboxEvent = new CallEventOutboxEntity();
        CallAnalysisOrchestratorService service = new CallAnalysisOrchestratorService(
                objectMapper,
                shardingRouter,
                callRoundRepository,
                analysisClient,
                resultService,
                outboxMapper,
                outboxEventFactory,
                new PostprocessPropertiesBuilder(false).build()
        );
        DomainEventMessage event = persistedEvent(objectMapper, recordEntity());

        when(outboxEventFactory.analysisCompleted(any(CallRecordEntity.class))).thenReturn(outboxEvent);

        service.handlePersistedEvent(event);

        verifyNoInteractions(analysisClient, shardingRouter, callRoundRepository);
        verify(resultService).findByTenantIdAndCallId(9L, 1001L);
        verifyNoMoreInteractions(resultService);
        verify(outboxMapper).batchInsert(List.of(outboxEvent));
    }

    @Test
    void shouldSaveDegradedResultAndCreateCompletionOutboxAfterRetryExhausted() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        MybatisCallRoundRepository callRoundRepository = mock(MybatisCallRoundRepository.class);
        CallAnalysisClient analysisClient = mock(CallAnalysisClient.class);
        CallAnalysisResultService resultService = mock(CallAnalysisResultService.class);
        CallEventOutboxMapper outboxMapper = mock(CallEventOutboxMapper.class);
        OutboxEventFactory outboxEventFactory = mock(OutboxEventFactory.class);
        CallEventOutboxEntity outboxEvent = new CallEventOutboxEntity();
        CallAnalysisOrchestratorService service = new CallAnalysisOrchestratorService(
                objectMapper,
                shardingRouter,
                callRoundRepository,
                analysisClient,
                resultService,
                outboxMapper,
                outboxEventFactory,
                new PostprocessPropertiesBuilder(true).build()
        );
        DomainEventMessage event = persistedEvent(objectMapper, recordEntity());
        ShardKey shardKey = new ShardKey(9L, 0, 3, "202605");

        when(shardingRouter.routeRound(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0))).thenReturn(shardKey);
        when(callRoundRepository.listByCallId(shardKey, 1001L)).thenReturn(List.of(roundEntity()));
        when(outboxEventFactory.analysisCompleted(any(CallRecordEntity.class))).thenReturn(outboxEvent);
        when(analysisClient.analyze(any(CallAnalysisRequest.class))).thenThrow(new IllegalStateException("llm timeout"));

        service.handlePersistedEvent(event, 3, 3);

        verify(resultService).saveDegraded(9L, 1001L, "java.lang.IllegalStateException: llm timeout");
        verify(outboxMapper).batchInsert(List.of(outboxEvent));
    }

    private static DomainEventMessage persistedEvent(ObjectMapper objectMapper, CallRecordEntity record) {
        return new DomainEventMessage(
                "call_record_persisted:9:1001",
                "call_record_persisted",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(record)
        );
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
