package com.callcenter.ingestion.inbound.analysis;

import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.application.analysis.CallAnalysisOrchestratorService;
import com.callcenter.ingestion.infrastructure.config.RocketMqProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.ai.group}",
        topic = "${call.postprocess.topics.record-persisted}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqCallAnalysisConsumer implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final CallAnalysisOrchestratorService orchestratorService;
    private final RocketMqProperties rocketMqProperties;

    public RocketMqCallAnalysisConsumer(
            ObjectMapper objectMapper,
            CallAnalysisOrchestratorService orchestratorService,
            RocketMqProperties rocketMqProperties
    ) {
        this.objectMapper = objectMapper;
        this.orchestratorService = orchestratorService;
        this.rocketMqProperties = rocketMqProperties;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            DomainEventMessage event = objectMapper.readValue(
                    new String(messageExt.getBody(), StandardCharsets.UTF_8),
                    DomainEventMessage.class
            );
            if (!"call_record_persisted".equals(event.eventType())) {
                throw new IllegalArgumentException("不支持的分析事件类型: " + event.eventType());
            }
            orchestratorService.handlePersistedEvent(
                    event,
                    messageExt.getReconsumeTimes(),
                    rocketMqProperties.getConsumers().getAi().getMaxReconsumeTimes()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ 分析事件失败", exception);
        }
    }
}
