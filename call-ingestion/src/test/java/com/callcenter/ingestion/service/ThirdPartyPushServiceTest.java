package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.model.ThirdPartyPushRequest;
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
import static org.mockito.Mockito.when;

class ThirdPartyPushServiceTest {

    @Test
    void shouldBuildPushPayloadFromRecordRoundsAndAnalysisResult() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        ShardingRouter shardingRouter = mock(ShardingRouter.class);
        CallRoundMysqlService callRoundMysqlService = mock(CallRoundMysqlService.class);
        CallAnalysisResultService callAnalysisResultService = mock(CallAnalysisResultService.class);
        ThirdPartyPushClient pushClient = mock(ThirdPartyPushClient.class);
        ThirdPartyPushService service = new ThirdPartyPushService(
                objectMapper,
                shardingRouter,
                callRoundMysqlService,
                callAnalysisResultService,
                pushClient
        );
        ShardKey shardKey = new ShardKey(9L, 0, 3, "202605");

        when(shardingRouter.routeRound(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0))).thenReturn(shardKey);
        when(callRoundMysqlService.listByCallId(shardKey, 1001L)).thenReturn(List.of(roundEntity()));
        when(callAnalysisResultService.findByTenantIdAndCallId(9L, 1001L)).thenReturn(analysisResultEntity());

        service.pushAnalysisCompletedEvent(event(objectMapper));

        ArgumentCaptor<ThirdPartyPushRequest> captor = ArgumentCaptor.forClass(ThirdPartyPushRequest.class);
        verify(pushClient).push(captor.capture());
        ThirdPartyPushRequest request = captor.getValue();
        assertThat(request.record().getCallId()).isEqualTo(1001L);
        assertThat(request.rounds()).hasSize(1);
        assertThat(request.analysisResult()).isNotNull();
        assertThat(request.analysisResult().getTags()).isEqualTo("[\"RISK\"]");
    }

    private static DomainEventMessage event(ObjectMapper objectMapper) {
        return new DomainEventMessage(
                "call_record_analysis_completed:9:1001",
                "call_record_analysis_completed",
                "CALL_RECORD",
                "1001",
                9L,
                Instant.parse("2026-05-20T06:00:00Z"),
                1,
                objectMapper.valueToTree(recordEntity())
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
}
