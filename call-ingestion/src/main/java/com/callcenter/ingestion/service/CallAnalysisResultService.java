package com.callcenter.ingestion.service;

import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.mapper.CallAnalysisResultMapper;
import com.callcenter.common.model.AnalysisResultStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallAnalysisResultService {

    private final CallAnalysisResultMapper mapper;
    private final ObjectMapper objectMapper;

    public CallAnalysisResultService(CallAnalysisResultMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
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
        CallAnalysisResultEntity entity = baseEntity(tenantId, callId, AnalysisResultStatus.SUCCEEDED);
        entity.setTags(writeTags(tags));
        entity.setRiskFlag(riskFlag);
        entity.setQualityScore(qualityScore);
        entity.setAiVersion(aiVersion);
        entity.setErrorMessage(null);
        mapper.upsert(entity);
    }

    public void saveDegraded(long tenantId, long callId, String errorMessage) {
        CallAnalysisResultEntity entity = baseEntity(tenantId, callId, AnalysisResultStatus.DEGRADED);
        entity.setTags(writeTags(List.of()));
        entity.setRiskFlag(null);
        entity.setQualityScore(null);
        entity.setAiVersion(null);
        entity.setErrorMessage(errorMessage);
        mapper.upsert(entity);
    }

    public CallAnalysisResultEntity findByTenantIdAndCallId(long tenantId, long callId) {
        return mapper.selectByTenantIdAndCallId(tenantId, callId);
    }

    private CallAnalysisResultEntity baseEntity(long tenantId, long callId, AnalysisResultStatus status) {
        LocalDateTime now = LocalDateTime.now();
        CallAnalysisResultEntity entity = new CallAnalysisResultEntity();
        entity.setTenantId(tenantId);
        entity.setCallId(callId);
        entity.setStatus(status.name());
        entity.setCompletedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化分析标签失败", exception);
        }
    }
}
