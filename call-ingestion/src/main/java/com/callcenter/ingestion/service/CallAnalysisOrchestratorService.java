package com.callcenter.ingestion.service;

import com.callcenter.ingestion.service.CallAnalysisGateway;
import com.callcenter.ingestion.service.PostprocessSettings;
import com.callcenter.ingestion.service.RoundRepository;
import com.callcenter.ingestion.service.OutboxEventRepository;
import com.callcenter.ingestion.model.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.model.CallRecordPersistedEvent;
import com.callcenter.ingestion.model.AnalysisResultData;
import com.callcenter.ingestion.model.DomainEventMessage;
import com.callcenter.ingestion.model.CallRecordData;
import com.callcenter.ingestion.model.CallRoundData;
import com.callcenter.ingestion.postprocess.OutboxEventFactory;
import com.callcenter.ingestion.model.CallAnalysisRequest;
import com.callcenter.ingestion.model.CallAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通话分析编排服务。
 * 串起幂等检查、回合加载、LLM 分析、结果落库以及后续 outbox 事件发布。
 */
@Service
public class CallAnalysisOrchestratorService {

    private final ObjectMapper objectMapper;
    private final RoundRepository callRoundRepository;
    private final CallAnalysisGateway analysisGateway;
    private final CallAnalysisResultService resultService;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final PostprocessSettings postprocessSettings;

    public CallAnalysisOrchestratorService(
            ObjectMapper objectMapper,
            RoundRepository callRoundRepository,
            CallAnalysisGateway analysisGateway,
            CallAnalysisResultService resultService,
            OutboxEventRepository outboxEventRepository,
            OutboxEventFactory outboxEventFactory,
            PostprocessSettings postprocessSettings
    ) {
        this.objectMapper = objectMapper;
        this.callRoundRepository = callRoundRepository;
        this.analysisGateway = analysisGateway;
        this.resultService = resultService;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
        this.postprocessSettings = postprocessSettings;
    }

    @Transactional
    public void handlePersistedEvent(DomainEventMessage event) {
        handlePersistedEvent(event, 0, Integer.MAX_VALUE);
    }

    @Transactional
    public void handlePersistedEvent(DomainEventMessage event, int reconsumeTimes, int maxReconsumeTimes) {
        CallRecordData record = deserializeRecord(event);
        AnalysisResultData existing = resultService.findByTenantIdAndCallId(record.tenantId(), record.callId());
        if (existing != null) {
            // 重复事件直接忽略，保证 MQ 重投和补偿流程不会重复写分析结果。
            return;
        }

        if (!postprocessSettings.llmEnabled()) {
            // 关闭 LLM 时仍然要推进链路，直接发送分析完成事件让下游按降级模式处理。
            publishAnalysisCompleted(record);
            return;
        }

        List<CallRoundData> rounds = loadRounds(record);
        try {
            CallAnalysisResponse response = analysisGateway.analyze(new CallAnalysisRequest(record, rounds));
            resultService.saveSucceeded(
                    record.tenantId(),
                    record.callId(),
                    response.tags(),
                    response.riskFlag(),
                    response.qualityScore(),
                    response.aiVersion()
            );
            publishAnalysisCompleted(record);
        } catch (RuntimeException exception) {
            if (reconsumeTimes >= maxReconsumeTimes) {
                // 重试上限后写入降级结果，确保整条后处理链路最终可收敛。
                resultService.saveDegraded(record.tenantId(), record.callId(), rootMessage(exception));
                publishAnalysisCompleted(record);
                return;
            }
            throw exception;
        }
    }

    private CallRecordData deserializeRecord(DomainEventMessage event) {
        try {
            return objectMapper.treeToValue(event.payload(), CallRecordPersistedEvent.class).toRecordData();
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析通话落库事件", exception);
        }
    }

    private List<CallRoundData> loadRounds(CallRecordData record) {
        return callRoundRepository.findByCallId(record.tenantId(), record.callId(), record.startTime());
    }

    private void publishAnalysisCompleted(CallRecordData record) {
        // 通过 outbox 延迟投递，避免在当前事务里直接发 MQ 导致“库成功、消息失败”的不一致。
        outboxEventRepository.saveAll(List.of(outboxEventFactory.analysisCompleted(CallAnalysisCompletedEvent.from(record))));
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
