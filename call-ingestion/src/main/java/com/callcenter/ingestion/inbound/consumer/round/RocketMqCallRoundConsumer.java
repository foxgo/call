package com.callcenter.ingestion.inbound.consumer.round;

import com.callcenter.ingestion.application.round.CallRoundIngestionService;
import com.callcenter.ingestion.domain.round.CallRoundMessage;
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
        consumerGroup = "${call.rocketmq.consumers.round.group}",
        topic = "${call.rocketmq.topics.round-ingest}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqCallRoundConsumer extends BaseRocketMQListener implements RocketMQListener<MessageExt> {

    private final ObjectMapper objectMapper;
    private final CallRoundIngestionService roundIngestionService;

    public RocketMqCallRoundConsumer(
            ObjectMapper objectMapper,
            CallRoundIngestionService roundIngestionService
    ) {
        this.objectMapper = objectMapper;
        this.roundIngestionService = roundIngestionService;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            CallRoundMessage message = objectMapper.readValue(
                    new String(messageExt.getBody(), StandardCharsets.UTF_8),
                    CallRoundMessage.class
            );
            boolean processed = roundIngestionService.process(new InboundMessage<>(
                    messageExt.getTopic(),
                    messageExt.getQueueId(),
                    messageExt.getQueueOffset(),
                    String.valueOf(message.tenantId()),
                    MessageType.ROUND,
                    MessageKeys.roundIdempotencyKey(message),
                    message,
                    0,
                    0L
            ));
            if (!processed) {
                throw new IllegalStateException("round 消费未完成，等待 RocketMQ 重试");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ round 写入消息失败", exception);
        }
    }
}
