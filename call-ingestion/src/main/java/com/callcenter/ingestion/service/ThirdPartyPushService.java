package com.callcenter.ingestion.service;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.common.entity.CallAnalysisResultEntity;
import com.callcenter.common.entity.CallRecordEntity;
import com.callcenter.common.entity.CallRoundEntity;
import com.callcenter.common.route.ShardKey;
import com.callcenter.common.route.ShardingRouter;
import com.callcenter.ingestion.model.ThirdPartyPushRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 第三方推送服务。
 * 在分析完成事件到达后，拼装主记录、回合和分析结果，生成对外推送载荷。
 */
@Service
public class ThirdPartyPushService {

    private final ObjectMapper objectMapper;
    private final ShardingRouter shardingRouter;
    private final CallRoundMysqlService callRoundMysqlService;
    private final CallAnalysisResultService callAnalysisResultService;
    private final ThirdPartyPushClient pushClient;

    public ThirdPartyPushService(
            ObjectMapper objectMapper,
            ShardingRouter shardingRouter,
            CallRoundMysqlService callRoundMysqlService,
            CallAnalysisResultService callAnalysisResultService,
            ThirdPartyPushClient pushClient
    ) {
        this.objectMapper = objectMapper;
        this.shardingRouter = shardingRouter;
        this.callRoundMysqlService = callRoundMysqlService;
        this.callAnalysisResultService = callAnalysisResultService;
        this.pushClient = pushClient;
    }

    public void pushAnalysisCompletedEvent(DomainEventMessage event) {
        CallRecordEntity record = deserializeRecord(event);
        ShardKey shardKey = shardingRouter.routeRound(record.getTenantId(), record.getCallId(), record.getStartTime());
        List<CallRoundEntity> rounds = callRoundMysqlService.listByCallId(shardKey, record.getCallId());
        CallAnalysisResultEntity analysisResult =
                callAnalysisResultService.findByTenantIdAndCallId(record.getTenantId(), record.getCallId());
        // 推送时统一基于落库后的真实数据组装，避免直接依赖事件载荷导致字段不全或格式漂移。
        pushClient.push(new ThirdPartyPushRequest(record, rounds, analysisResult));
    }

    private CallRecordEntity deserializeRecord(DomainEventMessage event) {
        try {
            return objectMapper.treeToValue(event.payload(), CallRecordEntity.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析第三方推送事件", exception);
        }
    }
}
