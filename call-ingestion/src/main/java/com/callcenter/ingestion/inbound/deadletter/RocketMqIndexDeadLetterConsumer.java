package com.callcenter.ingestion.inbound.deadletter;

import com.callcenter.ingestion.application.deadletter.DeadLetterTaskService;
import com.callcenter.ingestion.domain.shared.MessageType;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.index-dlq.group}",
        topic = "%DLQ%${call.rocketmq.consumers.index.group}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqIndexDeadLetterConsumer extends AbstractRocketMqDeadLetterConsumer {

    public RocketMqIndexDeadLetterConsumer(
            DeadLetterTaskService deadLetterTaskService
    ) {
        super(deadLetterTaskService, MessageType.INDEX);
    }
}
