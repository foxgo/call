package com.callcenter.ingestion.application.analysis;

import com.callcenter.ingestion.domain.analysis.AnalysisResultStatus;
import com.callcenter.ingestion.infrastructure.analysis.persistence.CallAnalysisResultEntity;
import com.callcenter.ingestion.infrastructure.analysis.persistence.CallAnalysisResultMapper;
import com.callcenter.ingestion.testsupport.JsonSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CallAnalysisResultServiceTest {

    @Test
    void shouldUpsertSucceededResultByTenantIdAndCallId() {
        CallAnalysisResultMapper mapper = mock(CallAnalysisResultMapper.class);
        CallAnalysisResultService service = new CallAnalysisResultService(mapper, JsonSupport.objectMapper());

        service.saveSucceeded(9L, 1001L, List.of("RISK"), true, 0.92f, "v1");

        ArgumentCaptor<CallAnalysisResultEntity> captor = ArgumentCaptor.forClass(CallAnalysisResultEntity.class);
        verify(mapper).upsert(captor.capture());
        CallAnalysisResultEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(9L);
        assertThat(entity.getCallId()).isEqualTo(1001L);
        assertThat(entity.getStatus()).isEqualTo(AnalysisResultStatus.SUCCEEDED.name());
        assertThat(entity.getTags()).isEqualTo("[\"RISK\"]");
        assertThat(entity.getRiskFlag()).isTrue();
        assertThat(entity.getQualityScore()).isEqualTo(0.92f);
        assertThat(entity.getAiVersion()).isEqualTo("v1");
        assertThat(entity.getErrorMessage()).isNull();
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldUpsertDegradedResultWithErrorMessage() {
        CallAnalysisResultMapper mapper = mock(CallAnalysisResultMapper.class);
        CallAnalysisResultService service = new CallAnalysisResultService(mapper, JsonSupport.objectMapper());

        service.saveDegraded(9L, 1001L, "llm timeout");

        ArgumentCaptor<CallAnalysisResultEntity> captor = ArgumentCaptor.forClass(CallAnalysisResultEntity.class);
        verify(mapper).upsert(captor.capture());
        CallAnalysisResultEntity entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(9L);
        assertThat(entity.getCallId()).isEqualTo(1001L);
        assertThat(entity.getStatus()).isEqualTo(AnalysisResultStatus.DEGRADED.name());
        assertThat(entity.getTags()).isEqualTo("[]");
        assertThat(entity.getErrorMessage()).isEqualTo("llm timeout");
        assertThat(entity.getCompletedAt()).isNotNull();
    }
}
