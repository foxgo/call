package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.model.AnalysisResultData;

public interface AnalysisResultRepository {

    void upsert(AnalysisResultData result);

    AnalysisResultData findByTenantIdAndCallId(long tenantId, long callId);
}
