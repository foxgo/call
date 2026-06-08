package com.callcenter.ingestion.inbound.consumer.record;

import com.callcenter.ingestion.application.record.CallRecordIngestionService;
import com.callcenter.ingestion.domain.record.CallRecordMessage;
import com.callcenter.ingestion.domain.shared.InboundMessage;
import com.callcenter.ingestion.domain.shared.MessageKeys;
import com.callcenter.ingestion.domain.shared.MessageType;
import com.callcenter.ingestion.infrastructure.mq.BaseRocketMQListener;
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
            CallRecordMessage message = objectMapper.readValue(
                    new String(messageExt.getBody(), StandardCharsets.UTF_8),
                    CallRecordMessage.class
            );
            boolean processed = recordIngestionService.process(new InboundMessage<>(
                    messageExt.getTopic(),
                    messageExt.getQueueId(),
                    messageExt.getQueueOffset(),
                    String.valueOf(message.tenantId()),
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
