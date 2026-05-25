package com.callcenter.ingestion.model;

import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import java.util.List;

public record ThirdPartyPushRequest(
        CallRecordEntity record,
        List<CallRoundEntity> rounds,
        CallAnalysisResultEntity analysisResult
) {
}
