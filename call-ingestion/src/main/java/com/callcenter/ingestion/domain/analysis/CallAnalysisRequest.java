package com.callcenter.ingestion.domain.analysis;

import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
import java.util.List;

public record CallAnalysisRequest(
        CallRecordData record,
        List<CallRoundData> rounds
) {
}
