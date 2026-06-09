package com.callcenter.ingestion.consumer;

import com.callcenter.ingestion.service.DeadLetterTaskService;
import com.callcenter.ingestion.model.MessageType;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

@RocketMQMessageListener(
        consumerGroup = "${call.rocketmq.consumers.record-dlq.group}",
        topic = "%DLQ%${call.rocketmq.consumers.record.group}",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        nameServer = "${call.rocketmq.name-server}"
)
@Component
public class RocketMqRecordDeadLetterConsumer extends AbstractRocketMqDeadLetterConsumer {

    public RocketMqRecordDeadLetterConsumer(
            DeadLetterTaskService deadLetterTaskService
    ) {
        super(deadLetterTaskService, MessageType.RECORD);
    }
}
