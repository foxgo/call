package com.callcenter.ingestion.inbound.consumer.deadletter;

import com.callcenter.ingestion.application.deadletter.DeadLetterTaskService;
import com.callcenter.ingestion.domain.shared.MessageType;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQListener;

public abstract class AbstractRocketMqDeadLetterConsumer implements RocketMQListener<MessageExt> {

    private final DeadLetterTaskService deadLetterTaskService;
    private final MessageType messageType;

    protected AbstractRocketMqDeadLetterConsumer(
            DeadLetterTaskService deadLetterTaskService,
            MessageType messageType
    ) {
        this.deadLetterTaskService = deadLetterTaskService;
        this.messageType = messageType;
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            deadLetterTaskService.persist(messageExt, messageType);
        } catch (Exception exception) {
            throw new IllegalStateException("处理 RocketMQ 死信消息失败", exception);
        }
    }
}
