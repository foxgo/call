package com.callcenter.ingestion.service;

import com.callcenter.ingestion.model.AnalysisResultData;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRoundData;
import java.util.List;

public interface SearchIndexGateway {

    void bulkIndexRecordData(List<CallRecordData> records);

    void bulkIndexRecordData(List<CallRecordData> records, List<AnalysisResultData> analysisResults);

    void bulkIndexRoundData(List<CallRoundData> rounds);
}
