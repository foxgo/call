package com.callcenter.ingestion.infrastructure.analysis.persistence;

import com.callcenter.ingestion.application.port.AnalysisResultRepository;
import com.callcenter.ingestion.domain.model.AnalysisResultData;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAnalysisResultRepository implements AnalysisResultRepository {

    private final CallAnalysisResultMapper mapper;

    public MybatisAnalysisResultRepository(CallAnalysisResultMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(AnalysisResultData result) {
        mapper.upsert(toEntity(result));
    }

    @Override
    public AnalysisResultData findByTenantIdAndCallId(long tenantId, long callId) {
        CallAnalysisResultEntity entity = mapper.selectByTenantIdAndCallId(tenantId, callId);
        return entity == null ? null : toData(entity);
    }

    private CallAnalysisResultEntity toEntity(AnalysisResultData result) {
        CallAnalysisResultEntity entity = new CallAnalysisResultEntity();
        entity.setTenantId(result.tenantId());
        entity.setCallId(result.callId());
        entity.setStatus(result.status());
        entity.setTags(result.tags());
        entity.setRiskFlag(result.riskFlag());
        entity.setQualityScore(result.qualityScore());
        entity.setAiVersion(result.aiVersion());
        entity.setErrorMessage(result.errorMessage());
        entity.setCompletedAt(result.completedAt());
        entity.setCreatedAt(result.completedAt());
        entity.setUpdatedAt(result.completedAt());
        return entity;
    }

    private AnalysisResultData toData(CallAnalysisResultEntity entity) {
        return new AnalysisResultData(
                entity.getTenantId(),
                entity.getCallId(),
                entity.getStatus(),
                entity.getTags(),
                entity.getRiskFlag(),
                entity.getQualityScore(),
                entity.getAiVersion(),
                entity.getErrorMessage(),
                entity.getCompletedAt()
        );
    }
}
