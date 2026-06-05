package com.callcenter.ingestion.infrastructure.analysis.client;

import com.callcenter.ingestion.domain.analysis.CallAnalysisRequest;
import com.callcenter.ingestion.domain.analysis.CallAnalysisResponse;

public interface CallAnalysisClient {

    CallAnalysisResponse analyze(CallAnalysisRequest request);
}
