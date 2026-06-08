package com.callcenter.ingestion.application.postprocess;

import com.callcenter.ingestion.application.port.RoundRepository;
import com.callcenter.ingestion.application.port.ThirdPartyPushGateway;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.domain.event.CallAnalysisCompletedEvent;
import com.callcenter.ingestion.domain.postprocess.ThirdPartyPushRequest;
import com.callcenter.ingestion.application.analysis.CallAnalysisResultService;
import com.callcenter.ingestion.domain.model.AnalysisResultData;
import com.callcenter.ingestion.domain.model.CallRecordData;
import com.callcenter.ingestion.domain.model.CallRoundData;
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
    private final RoundRepository callRoundRepository;
    private final CallAnalysisResultService callAnalysisResultService;
    private final ThirdPartyPushGateway pushGateway;

    public ThirdPartyPushService(
            ObjectMapper objectMapper,
            RoundRepository callRoundRepository,
            CallAnalysisResultService callAnalysisResultService,
            ThirdPartyPushGateway pushGateway
    ) {
        this.objectMapper = objectMapper;
        this.callRoundRepository = callRoundRepository;
        this.callAnalysisResultService = callAnalysisResultService;
        this.pushGateway = pushGateway;
    }

    public void pushAnalysisCompletedEvent(DomainEventMessage event) {
        CallRecordData record = deserializeRecord(event);
        List<CallRoundData> rounds = callRoundRepository.findByCallId(record.tenantId(), record.callId(), record.startTime());
        AnalysisResultData analysisResult =
                callAnalysisResultService.findByTenantIdAndCallId(record.tenantId(), record.callId());
        // 推送时统一基于落库后的真实数据组装，避免直接依赖事件载荷导致字段不全或格式漂移。
        pushGateway.push(new ThirdPartyPushRequest(record, rounds, analysisResult));
    }

    private CallRecordData deserializeRecord(DomainEventMessage event) {
        try {
            return objectMapper.treeToValue(event.payload(), CallAnalysisCompletedEvent.class).toRecordData();
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析第三方推送事件", exception);
        }
    }
}
