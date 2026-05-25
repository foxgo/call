package com.callcenter.ingestion.consumer;

import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.service.DeadLetterTaskService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.round-dlq.group}",
        topic = "%DLQ%${call.rocketmq.consumers.round.group}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqRoundDeadLetterConsumer extends AbstractRocketMqDeadLetterConsumer {

    public RocketMqRoundDeadLetterConsumer(
            DeadLetterTaskService deadLetterTaskService
    ) {
        super(deadLetterTaskService, MessageType.ROUND);
    }
}
