package com.callcenter.ingestion.consumer;

import com.callcenter.ingestion.postprocess.ThirdPartyPushService;
import com.callcenter.ingestion.model.DomainEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.third-party.group}",
        topic = "${call.postprocess.topics.analysis-completed}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqThirdPartyPushConsumer implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final ThirdPartyPushService pushService;

    public RocketMqThirdPartyPushConsumer(ObjectMapper objectMapper, ThirdPartyPushService pushService) {
        this.objectMapper = objectMapper;
        this.pushService = pushService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            DomainEventMessage event = objectMapper.readValue(
                    new String(messageExt.getBody(), StandardCharsets.UTF_8),
                    DomainEventMessage.class
            );
            if (!"call_record_analysis_completed".equals(event.eventType())) {
                throw new IllegalArgumentException("不支持的第三方推送事件类型: " + event.eventType());
            }
            pushService.pushAnalysisCompletedEvent(event);
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ 第三方推送事件失败", exception);
        }
    }
}
