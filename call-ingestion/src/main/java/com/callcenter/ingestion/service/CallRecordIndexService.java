package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIndexService {

    private final ObjectMapper objectMapper;
    private final ElasticsearchBulkService elasticsearchBulkService;
    private final ShardingRouter shardingRouter;
    private final CallRoundMysqlService callRoundMysqlService;
    private final CallAnalysisResultService callAnalysisResultService;

    public CallRecordIndexService(
            ObjectMapper objectMapper,
            ElasticsearchBulkService elasticsearchBulkService,
            ShardingRouter shardingRouter,
            CallRoundMysqlService callRoundMysqlService,
            CallAnalysisResultService callAnalysisResultService
    ) {
        this.objectMapper = objectMapper;
        this.elasticsearchBulkService = elasticsearchBulkService;
        this.shardingRouter = shardingRouter;
        this.callRoundMysqlService = callRoundMysqlService;
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
        return callRoundMysqlService.listByCallId(roundShardKey, record.getCallId());
    }
}
