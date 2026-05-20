package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.ingestion.config.PostprocessProperties;
import com.callcenter.ingestion.service.CallRecordIndexService;
import com.callcenter.ingestion.service.CallRoundIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.index.group}",
        topic = "${call.postprocess.topics.record-persisted}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqPersistedEventConsumer
        implements RocketMQListener<MessageExt>, RocketMQPushConsumerLifecycleListener {

    private final ObjectMapper objectMapper;
    private final CallRecordIndexService recordIndexService;
    private final CallRoundIndexService roundIndexService;
    private final PostprocessProperties postprocessProperties;

    public RocketMqPersistedEventConsumer(
            ObjectMapper objectMapper,
            CallRecordIndexService recordIndexService,
            CallRoundIndexService roundIndexService,
            PostprocessProperties postprocessProperties
    ) {
        this.objectMapper = objectMapper;
        this.recordIndexService = recordIndexService;
        this.roundIndexService = roundIndexService;
        this.postprocessProperties = postprocessProperties;
    }

    @Override
    public void onMessage(MessageExt message) {
        try {
            DomainEventMessage event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    DomainEventMessage.class
            );
            switch (event.eventType()) {
                case "call_record_persisted" -> recordIndexService.indexPersistedEvent(event);
                case "call_round_persisted" -> roundIndexService.indexPersistedEvent(event);
                default -> throw new IllegalArgumentException("不支持的落库事件类型: " + event.eventType());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ 落库事件失败", exception);
        }
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        String recordTopic = postprocessProperties.getTopics().getRecordPersisted();
        String roundTopic = postprocessProperties.getTopics().getRoundPersisted();
        if (recordTopic.equals(roundTopic)) {
            return;
        }
        try {
            consumer.subscribe(roundTopic, "*");
        } catch (Exception exception) {
            throw new IllegalStateException("订阅 round-persisted 主题失败", exception);
        }
    }
}
