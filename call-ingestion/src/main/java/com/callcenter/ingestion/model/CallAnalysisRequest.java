package com.callcenter.ingestion.model;

import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import java.util.List;

public record CallAnalysisRequest(
        CallRecordEntity record,
        List<CallRoundEntity> rounds
) {
}
