package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.AnalysisResultData;

public interface AnalysisResultRepository {

    void upsert(AnalysisResultData result);

    AnalysisResultData findByTenantIdAndCallId(long tenantId, long callId);
}
