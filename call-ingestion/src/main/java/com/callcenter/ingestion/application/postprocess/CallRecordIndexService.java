package com.callcenter.ingestion.application.postprocess;

import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.infrastructure.analysis.persistence.CallAnalysisResultEntity;
import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.round.persistence.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.application.analysis.CallAnalysisResultService;
import com.callcenter.ingestion.infrastructure.postprocess.search.ElasticsearchBulkService;
import com.callcenter.ingestion.infrastructure.round.persistence.MybatisCallRoundRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIndexService {

    private final ObjectMapper objectMapper;
    private final ElasticsearchBulkService elasticsearchBulkService;
    private final ShardingRouter shardingRouter;
    private final MybatisCallRoundRepository callRoundRepository;
    private final CallAnalysisResultService callAnalysisResultService;

    public CallRecordIndexService(
            ObjectMapper objectMapper,
            ElasticsearchBulkService elasticsearchBulkService,
            ShardingRouter shardingRouter,
            MybatisCallRoundRepository callRoundRepository,
            CallAnalysisResultService callAnalysisResultService
    ) {
        this.objectMapper = objectMapper;
        this.elasticsearchBulkService = elasticsearchBulkService;
        this.shardingRouter = shardingRouter;
        this.callRoundRepository = callRoundRepository;
        this.callAnalysisResultService = callAnalysisResultService;
    }

    public void indexBatch(List<CallRecordEntity> entities) {
        elasticsearchBulkService.bulkIndexRecords(entities);
    }

    public void indexAnalysisCompletedEvent(DomainEventMessage event) {
        try {
            CallRecordEntity record = objectMapper.treeToValue(event.payload(), CallRecordEntity.class);
            CallAnalysisResultEntity analysisResult =
                    callAnalysisResultService.findByTenantIdAndCallId(record.getTenantId(), record.getCallId());
            elasticsearchBulkService.bulkIndexRecords(
                    List.of(record),
                    analysisResult == null ? Map.of() : Map.of(record.getCallId(), analysisResult)
            );
            List<CallRoundEntity> rounds = loadCallRounds(record);
            if (!rounds.isEmpty()) {
                elasticsearchBulkService.bulkIndexRounds(rounds);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to deserialize record persisted event payload", exception);
        }
    }

    private List<CallRoundEntity> loadCallRounds(CallRecordEntity record) {
        ShardKey roundShardKey = shardingRouter.routeRound(
                record.getTenantId(),
                record.getCallId(),
                record.getStartTime()
        );
        return callRoundRepository.listByCallId(roundShardKey, record.getCallId());
    }
}
