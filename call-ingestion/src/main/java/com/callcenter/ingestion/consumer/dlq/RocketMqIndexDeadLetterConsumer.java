package com.callcenter.ingestion.consumer.dlq;

import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.service.DeadLetterTaskService;
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
