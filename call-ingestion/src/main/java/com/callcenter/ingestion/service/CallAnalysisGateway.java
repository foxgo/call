package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.CallAnalysisRequest;
import com.callcenter.ingestion.model.CallAnalysisResponse;

public interface CallAnalysisGateway {

    CallAnalysisResponse analyze(CallAnalysisRequest request);
}
