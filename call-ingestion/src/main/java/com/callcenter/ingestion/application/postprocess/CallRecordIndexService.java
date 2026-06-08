package com.callcenter.ingestion.application.postprocess;

import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.application.port.SearchIndexGateway;
import com.callcenter.ingestion.domain.event.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.domain.model.AnalysisResultData;
import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
import com.callcenter.ingestion.application.analysis.CallAnalysisResultService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIndexService {

    private final ObjectMapper objectMapper;
    private final SearchIndexGateway searchIndexGateway;
    private final RoundRepository callRoundRepository;
    private final CallAnalysisResultService callAnalysisResultService;

    public CallRecordIndexService(
            ObjectMapper objectMapper,
            SearchIndexGateway searchIndexGateway,
            RoundRepository callRoundRepository,
            CallAnalysisResultService callAnalysisResultService
    ) {
        this.objectMapper = objectMapper;
        this.searchIndexGateway = searchIndexGateway;
        this.callRoundRepository = callRoundRepository;
        this.callAnalysisResultService = callAnalysisResultService;
    }

    public void indexBatch(List<CallRecordData> entities) {
        searchIndexGateway.bulkIndexRecordData(entities);
    }

    public void indexAnalysisCompletedEvent(DomainEventMessage event) {
        try {
            CallRecordData record = objectMapper.treeToValue(event.payload(), CallAnalysisCompletedEvent.class).toRecordData();
            AnalysisResultData analysisResult =
                    callAnalysisResultService.findByTenantIdAndCallId(record.tenantId(), record.callId());
            searchIndexGateway.bulkIndexRecordData(List.of(record), analysisResult == null ? List.of() : List.of(analysisResult));
            List<CallRoundData> rounds = loadCallRounds(record);
            if (!rounds.isEmpty()) {
                searchIndexGateway.bulkIndexRoundData(rounds);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize record persisted event payload", exception);
        }
    }

    private List<CallRoundData> loadCallRounds(CallRecordData record) {
        return callRoundRepository.findByCallId(record.tenantId(), record.callId(), record.startTime());
    }
}
