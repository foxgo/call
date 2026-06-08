package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.analysis.CallAnalysisRequest;
import com.callcenter.ingestion.domain.analysis.CallAnalysisResponse;

public interface CallAnalysisGateway {

    CallAnalysisResponse analyze(CallAnalysisRequest request);
}
