package com.callcenter.ingestion.domain.postprocess;

import com.callcenter.ingestion.domain.model.AnalysisResultData;
import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
import java.util.List;

public record ThirdPartyPushRequest(
        CallRecordData record,
        List<CallRoundData> rounds,
        AnalysisResultData analysisResult
) {
}
