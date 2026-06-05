package com.callcenter.ingestion.domain.postprocess;

import com.callcenter.ingestion.infrastructure.analysis.persistence.CallAnalysisResultEntity;
import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.round.persistence.CallRoundEntity;
import java.util.List;

public record ThirdPartyPushRequest(
        CallRecordEntity record,
        List<CallRoundEntity> rounds,
        CallAnalysisResultEntity analysisResult
) {
}
