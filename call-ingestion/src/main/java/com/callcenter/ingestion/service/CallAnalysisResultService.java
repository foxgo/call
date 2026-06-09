package com.callcenter.ingestion.service;

import com.callcenter.ingestion.service.AnalysisResultRepository;
import com.callcenter.ingestion.model.AnalysisResultStatus;
import com.callcenter.ingestion.model.AnalysisResultData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallAnalysisResultService {

    private final AnalysisResultRepository repository;
    private final ObjectMapper objectMapper;

    public CallAnalysisResultService(AnalysisResultRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void saveSucceeded(
            long tenantId,
            long callId,
            List<String> tags,
            Boolean riskFlag,
            Float qualityScore,
            String aiVersion
    ) {
        repository.upsert(new AnalysisResultData(
                tenantId,
                callId,
                AnalysisResultStatus.SUCCEEDED.name(),
                writeTags(tags),
                riskFlag,
                qualityScore,
                aiVersion,
                null,
                LocalDateTime.now()
        ));
    }

    public void saveDegraded(long tenantId, long callId, String errorMessage) {
        repository.upsert(new AnalysisResultData(
                tenantId,
                callId,
                AnalysisResultStatus.DEGRADED.name(),
                writeTags(List.of()),
                null,
                null,
                null,
                errorMessage,
                LocalDateTime.now()
        ));
    }

    public AnalysisResultData findByTenantIdAndCallId(long tenantId, long callId) {
        return repository.findByTenantIdAndCallId(tenantId, callId);
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化分析标签失败", exception);
        }
    }

}
