package com.callcenter.ingestion.inbound.postprocess;

import com.callcenter.ingestion.application.postprocess.CallRecordIndexService;
import com.callcenter.ingestion.domain.analysis.DomainEventMessage;
import com.callcenter.ingestion.infrastructure.mq.BaseRocketMQListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.index.group}",
        topic = "${call.postprocess.topics.analysis-completed}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqElasticSearchConsumer extends BaseRocketMQListener implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final CallRecordIndexService recordIndexService;

    public RocketMqElasticSearchConsumer(
            ObjectMapper objectMapper,
            CallRecordIndexService recordIndexService
    ) {
        this.objectMapper = objectMapper;
        this.recordIndexService = recordIndexService;
    }

    @Override
    public void onMessage(MessageExt message) {
        try {
            DomainEventMessage event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    DomainEventMessage.class
            );
            switch (event.eventType()) {
                case "call_record_analysis_completed" -> recordIndexService.indexAnalysisCompletedEvent(event);
                default -> throw new IllegalArgumentException("不支持的落库事件类型: " + event.eventType());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ 落库事件失败", exception);
        }
    }
}
