package com.callcenter.ingestion.application.analysis;

import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.infrastructure.analysis.persistence.CallAnalysisResultEntity;
import com.callcenter.ingestion.infrastructure.outbox.persistence.CallEventOutboxEntity;
import com.callcenter.ingestion.infrastructure.outbox.persistence.CallEventOutboxMapper;
import com.callcenter.ingestion.infrastructure.record.persistence.CallRecordEntity;
import com.callcenter.ingestion.infrastructure.round.persistence.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.application.outbox.OutboxEventFactory;
import com.callcenter.ingestion.domain.analysis.CallAnalysisRequest;
import com.callcenter.ingestion.domain.analysis.CallAnalysisResponse;
import com.callcenter.ingestion.infrastructure.config.PostprocessProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.callcenter.ingestion.infrastructure.analysis.client.CallAnalysisClient;
import com.callcenter.ingestion.infrastructure.round.persistence.MybatisCallRoundRepository;

/**
 * 通话分析编排服务。
 * 串起幂等检查、回合加载、LLM 分析、结果落库以及后续 outbox 事件发布。
 */
@Service
public class CallAnalysisOrchestratorService {

    private final ObjectMapper objectMapper;
    private final ShardingRouter shardingRouter;
    private final MybatisCallRoundRepository callRoundRepository;
    private final CallAnalysisClient analysisClient;
    private final CallAnalysisResultService resultService;
    private final CallEventOutboxMapper outboxMapper;
    private final OutboxEventFactory outboxEventFactory;
    private final PostprocessProperties postprocessProperties;

    public CallAnalysisOrchestratorService(
            ObjectMapper objectMapper,
            ShardingRouter shardingRouter,
            MybatisCallRoundRepository callRoundRepository,
            CallAnalysisClient analysisClient,
            CallAnalysisResultService resultService,
            CallEventOutboxMapper outboxMapper,
            OutboxEventFactory outboxEventFactory,
            PostprocessProperties postprocessProperties
    ) {
        this.objectMapper = objectMapper;
        this.shardingRouter = shardingRouter;
        this.callRoundRepository = callRoundRepository;
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
            // 重复事件直接忽略，保证 MQ 重投和补偿流程不会重复写分析结果。
            return;
        }

        if (!postprocessProperties.isLlmEnabled()) {
            // 关闭 LLM 时仍然要推进链路，直接发送分析完成事件让下游按降级模式处理。
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
                // 重试上限后写入降级结果，确保整条后处理链路最终可收敛。
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
        return callRoundRepository.listByCallId(shardKey, record.getCallId());
    }

    private void publishAnalysisCompleted(CallRecordEntity record) {
        // 通过 outbox 延迟投递，避免在当前事务里直接发 MQ 导致“库成功、消息失败”的不一致。
        CallEventOutboxEntity event = outboxEventFactory.analysisCompleted(record);
        outboxMapper.batchInsert(List.of(event));
    }

    private String rootMessage(Throwable throwable) {
        // 降级结果保留根异常，便于后续 DLQ/人工排障快速定位真正失败原因。
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getName() + ": " + root.getMessage();
    }
}
