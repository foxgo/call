package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CallRecordIndexService {

    private final ObjectMapper objectMapper;
    private final ElasticsearchBulkService elasticsearchBulkService;
    private final ShardingRouter shardingRouter;
    private final CallRoundMysqlService callRoundMysqlService;

    public CallRecordIndexService(
            ObjectMapper objectMapper,
            ElasticsearchBulkService elasticsearchBulkService,
            ShardingRouter shardingRouter,
            CallRoundMysqlService callRoundMysqlService
    ) {
        this.objectMapper = objectMapper;
        this.elasticsearchBulkService = elasticsearchBulkService;
        this.shardingRouter = shardingRouter;
        this.callRoundMysqlService = callRoundMysqlService;
    }

    public void indexBatch(List<CallRecordEntity> entities) {
        elasticsearchBulkService.bulkIndexRecords(entities);
    }

    public void indexPersistedEvent(DomainEventMessage event) {
        try {
            CallRecordEntity record = objectMapper.treeToValue(event.payload(), CallRecordEntity.class);
            elasticsearchBulkService.bulkIndexRecords(List.of(record));
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
