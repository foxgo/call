package com.callcenter.ingestion.domain.analysis;

import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.round.persistence.CallRoundEntity;
import java.util.List;

public record CallAnalysisRequest(
        CallRecordEntity record,
        List<CallRoundEntity> rounds
) {
}
