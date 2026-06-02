package com.callcenter.task.mq;

import com.callcenter.common.entity.CallDialUnitEntity;
import com.callcenter.task.config.CallTaskRocketMqProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

@Component
public class DialDispatchPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final CallTaskRocketMqProperties properties;

    public DialDispatchPublisher(RocketMQTemplate rocketMQTemplate, CallTaskRocketMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public void publish(CallDialUnitEntity entity) {
        DialDispatchMessage message = new DialDispatchMessage(
                entity.getId(),
                entity.getTenantId(),
                entity.getTaskId(),
                entity.getPhone(),
                entity.getDispatchToken(),
                entity.getSelectedCallerNumber(),
                entity.getAttemptStage(),
                entity.getCallerIdSelectionScore()
        );
        rocketMQTemplate.syncSend(properties.getTopics().getDispatch(), message);
    }
}
