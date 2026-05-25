package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.entity.CallEventOutboxEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.mapper.CallEventOutboxMapper;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.config.PostprocessProperties;
import com.callcenter.ingestion.model.CallAnalysisRequest;
import com.callcenter.ingestion.model.CallAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallAnalysisOrchestratorService {

    private final ObjectMapper objectMapper;
    private final ShardingRouter shardingRouter;
    private final CallRoundMysqlService callRoundMysqlService;
    private final CallAnalysisClient analysisClient;
    private final CallAnalysisResultService resultService;
    private final CallEventOutboxMapper outboxMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final PostprocessProperties postprocessProperties;

    public CallAnalysisOrchestratorService(
            ObjectMapper objectMapper,
            ShardingRouter shardingRouter,
            CallRoundMysqlService callRoundMysqlService,
            CallAnalysisClient analysisClient,
            CallAnalysisResultService resultService,
            CallEventOutboxMapper outboxMapper,
            OutboxEventFactory outboxEventFactory,
            PostprocessProperties postprocessProperties
    ) {
        this.objectMapper = objectMapper;
        this.shardingRouter = shardingRouter;
        this.callRoundMysqlService = callRoundMysqlService;
        this.analysisClient = analysisClient;
        this.resultService = resultService;
        this.outboxMapper = outboxMapper;
        this.outboxEventFactory = outboxEventFactory;
        this.postprocessProperties = postprocessProperties;
    }

    @Transactional
    public void handlePersistedEvent(DomainEventMessage event) {
        handlePersistedEvent(event, 0, Integer.MAX_VALUE);
    }

    @Transactional
    public void handlePersistedEvent(DomainEventMessage event, int reconsumeTimes, int maxReconsumeTimes) {
        CallRecordEntity record = deserializeRecord(event);
        CallAnalysisResultEntity existing = resultService.findByTenantIdAndCallId(record.getTenantId(), record.getCallId());
        if (existing != null) {
            return;
        }

        if (!postprocessProperties.isLlmEnabled()) {
            publishAnalysisCompleted(record);
            return;
        }

        List<CallRoundEntity> rounds = loadRounds(record);
        try {
            CallAnalysisResponse response = analysisClient.analyze(new CallAnalysisRequest(record, rounds));
            resultService.saveSucceeded(
                    record.getTenantId(),
                    record.getCallId(),
                    response.tags(),
                    response.riskFlag(),
                    response.qualityScore(),
                    response.aiVersion()
            );
            publishAnalysisCompleted(record);
        } catch (RuntimeException exception) {
            if (reconsumeTimes >= maxReconsumeTimes) {
                resultService.saveDegraded(record.getTenantId(), record.getCallId(), rootMessage(exception));
                publishAnalysisCompleted(record);
                return;
            }
            throw exception;
        }
    }

    private CallRecordEntity deserializeRecord(DomainEventMessage event) {
        try {
            return objectMapper.treeToValue(event.payload(), CallRecordEntity.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析通话落库事件", exception);
        }
    }

    private List<CallRoundEntity> loadRounds(CallRecordEntity record) {
        ShardKey shardKey = shardingRouter.routeRound(record.getTenantId(), record.getCallId(), record.getStartTime());
        return callRoundMysqlService.listByCallId(shardKey, record.getCallId());
    }

    private void publishAnalysisCompleted(CallRecordEntity record) {
        CallEventOutboxEntity event = outboxEventFactory.analysisCompleted(record);
        outboxMapper.batchInsert(List.of(event));
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }
}
