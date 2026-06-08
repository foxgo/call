package com.callcenter.ingestion.application.postprocess;

import com.callcenter.ingestion.application.analysis.CallAnalysisResultService;
import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.application.port.ThirdPartyPushGateway;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.domain.event.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.domain.model.AnalysisResultData;
import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
import com.callcenter.ingestion.domain.postprocess.ThirdPartyPushRequest;
import com.callcenter.ingestion.testsupport.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThirdPartyPushServiceTest {

    @Test
    void shouldBuildPushPayloadFromRecordRoundsAndAnalysisResult() {
        ObjectMapper objectMapper = JsonSupport.objectMapper();
        RoundRepository callRoundRepository = mock(RoundRepository.class);
        CallAnalysisResultService callAnalysisResultService = mock(CallAnalysisResultService.class);
        ThirdPartyPushGateway pushGateway = mock(ThirdPartyPushGateway.class);
        ThirdPartyPushService service = new ThirdPartyPushService(
                objectMapper,
                callRoundRepository,
                callAnalysisResultService,
                pushGateway
        );

        when(callRoundRepository.findByCallId(9L, 1001L, LocalDateTime.of(2026, 5, 20, 10, 0)))
                .thenReturn(List.of(roundData()));
        when(callAnalysisResultService.findByTenantIdAndCallId(9L, 1001L)).thenReturn(analysisResultData());

        service.pushAnalysisCompletedEvent(event(objectMapper));

        ArgumentCaptor<ThirdPartyPushRequest> captor = ArgumentCaptor.forClass(ThirdPartyPushRequest.class);
        verify(pushGateway).push(captor.capture());
        ThirdPartyPushRequest request = captor.getValue();
        assertThat(request.record().callId()).isEqualTo(1001L);
        assertThat(request.rounds()).hasSize(1);
        assertThat(request.analysisResult()).isNotNull();
        assertThat(request.analysisResult().tags()).isEqualTo("[\"RISK\"]");
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
                objectMapper.valueToTree(CallAnalysisCompletedEvent.from(recordData()))
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
}
