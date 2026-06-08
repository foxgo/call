package com.callcenter.ingestion.inbound.consumer.deadletter;

import com.callcenter.ingestion.application.deadletter.DeadLetterTaskService;
import com.callcenter.ingestion.domain.shared.MessageType;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.ai-dlq.group}",
        topic = "%DLQ%${call.rocketmq.consumers.ai.group}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqAiDeadLetterConsumer extends AbstractRocketMqDeadLetterConsumer {

    public RocketMqAiDeadLetterConsumer(
            DeadLetterTaskService deadLetterTaskService
    ) {
        super(deadLetterTaskService, MessageType.AI);
    }
}
