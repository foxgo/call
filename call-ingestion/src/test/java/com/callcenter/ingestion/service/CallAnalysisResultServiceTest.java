package com.callcenter.ingestion.service;

import com.callcenter.ingestion.service.AnalysisResultRepository;
import com.callcenter.ingestion.model.AnalysisResultStatus;
import com.callcenter.ingestion.model.AnalysisResultData;
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
        AnalysisResultRepository repository = mock(AnalysisResultRepository.class);
        CallAnalysisResultService service = new CallAnalysisResultService(repository, JsonSupport.objectMapper());

        service.saveSucceeded(9L, 1001L, List.of("RISK"), true, 0.92f, "v1");

        ArgumentCaptor<AnalysisResultData> captor = ArgumentCaptor.forClass(AnalysisResultData.class);
        verify(repository).upsert(captor.capture());
        AnalysisResultData entity = captor.getValue();
        assertThat(entity.tenantId()).isEqualTo(9L);
        assertThat(entity.callId()).isEqualTo(1001L);
        assertThat(entity.status()).isEqualTo(AnalysisResultStatus.SUCCEEDED.name());
        assertThat(entity.tags()).isEqualTo("[\"RISK\"]");
        assertThat(entity.riskFlag()).isTrue();
        assertThat(entity.qualityScore()).isEqualTo(0.92f);
        assertThat(entity.aiVersion()).isEqualTo("v1");
        assertThat(entity.errorMessage()).isNull();
        assertThat(entity.completedAt()).isNotNull();
    }

    @Test
    void shouldUpsertDegradedResultWithErrorMessage() {
        AnalysisResultRepository repository = mock(AnalysisResultRepository.class);
        CallAnalysisResultService service = new CallAnalysisResultService(repository, JsonSupport.objectMapper());

        service.saveDegraded(9L, 1001L, "llm timeout");

        ArgumentCaptor<AnalysisResultData> captor = ArgumentCaptor.forClass(AnalysisResultData.class);
        verify(repository).upsert(captor.capture());
        AnalysisResultData entity = captor.getValue();
        assertThat(entity.tenantId()).isEqualTo(9L);
        assertThat(entity.callId()).isEqualTo(1001L);
        assertThat(entity.status()).isEqualTo(AnalysisResultStatus.DEGRADED.name());
        assertThat(entity.tags()).isEqualTo("[]");
        assertThat(entity.errorMessage()).isEqualTo("llm timeout");
        assertThat(entity.completedAt()).isNotNull();
    }
}
