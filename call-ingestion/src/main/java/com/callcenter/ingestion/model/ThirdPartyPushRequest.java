package com.callcenter.ingestion.model;

import com.callcenter.ingestion.model.AnalysisResultData;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRoundData;
import java.util.List;

public record ThirdPartyPushRequest(
        CallRecordData record,
        List<CallRoundData> rounds,
        AnalysisResultData analysisResult
) {
}
