package com.callcenter.ingestion.consumer;

import com.callcenter.ingestion.model.MessageType;
import com.callcenter.ingestion.service.DeadLetterTaskService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.third-party-dlq.group}",
        topic = "%DLQ%${call.rocketmq.consumers.third-party.group}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqThirdPartyDeadLetterConsumer extends AbstractRocketMqDeadLetterConsumer {

    public RocketMqThirdPartyDeadLetterConsumer(
            DeadLetterTaskService deadLetterTaskService
    ) {
        super(deadLetterTaskService, MessageType.THIRD_PARTY);
    }
}
