package com.callcenter.ingestion.consumer;

import com.callcenter.common.dto.CallRecordMessage;
import com.callcenter.common.dto.DomainEventMessage;
import com.callcenter.ingestion.model.InboundMessage;
import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.processor.MessageKeys;
import com.callcenter.ingestion.service.CallRecordIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.record.group}",
        topic = "${call.rocketmq.topics.record-ingest}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqCallRecordConsumer extends BaseRocketMQListener implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final CallRecordIngestionService recordIngestionService;

    public RocketMqCallRecordConsumer(
            ObjectMapper objectMapper,
            CallRecordIngestionService recordIngestionService
    ) {
        this.objectMapper = objectMapper;
        this.recordIngestionService = recordIngestionService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            String payload = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            DomainEventMessage envelope = objectMapper.readValue(payload, DomainEventMessage.class);
            if (!"CALL_RECORD".equals(envelope.eventType())) {
                throw new IllegalArgumentException("不支持的写入事件类型: " + envelope.eventType());
            }

            CallRecordMessage message = objectMapper.treeToValue(envelope.payload(), CallRecordMessage.class);
            boolean processed = recordIngestionService.process(new InboundMessage<>(
                    messageExt.getTopic(),
                    messageExt.getQueueId(),
                    messageExt.getQueueOffset(),
                    String.valueOf(envelope.tenantId()),
                    MessageType.RECORD,
                    MessageKeys.recordIdempotencyKey(message),
                    message,
                    0,
                    0L
            ));
            if (!processed) {
                throw new IllegalStateException("record 消费未完成，等待 RocketMQ 重试");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ record 写入消息失败", exception);
        }
    }
}
