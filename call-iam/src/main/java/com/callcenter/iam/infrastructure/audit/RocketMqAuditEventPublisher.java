package com.callcenter.iam.infrastructure.audit;

import com.callcenter.iam.application.audit.AuditCommand;
import com.callcenter.iam.mq.AuditEventConsumer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class RocketMqAuditEventPublisher implements AuditEventPublisher {

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final ObjectProvider<AuditEventConsumer> auditEventConsumerProvider;

    public RocketMqAuditEventPublisher(
            ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
            ObjectProvider<AuditEventConsumer> auditEventConsumerProvider
    ) {
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
        this.auditEventConsumerProvider = auditEventConsumerProvider;
    }

    @Override
    public void publish(AuditCommand command) {
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate != null) {
            rocketMQTemplate.convertAndSend("iam-audit", command);
            return;
        }
        AuditEventConsumer consumer = auditEventConsumerProvider.getIfAvailable();
        if (consumer != null) {
            consumer.consume(command);
        }
    }
}
