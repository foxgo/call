package com.callcenter.ingestion.application.port;

import com.callcenter.ingestion.domain.model.AnalysisResultData;
import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
import java.util.List;

public interface SearchIndexGateway {

    void bulkIndexRecordData(List<CallRecordData> records);

    void bulkIndexRecordData(List<CallRecordData> records, List<AnalysisResultData> analysisResults);

    void bulkIndexRoundData(List<CallRoundData> rounds);
}
