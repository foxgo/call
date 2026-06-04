package com.callcenter.iam.mq;

import com.callcenter.iam.application.audit.AuditCommand;
import com.callcenter.iam.domain.audit.AuditLog;
import com.callcenter.iam.infrastructure.audit.AuditLogRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class AuditEventConsumer {

    private final AuditLogRepository auditLogRepository;

    public AuditEventConsumer(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void consume(AuditCommand command) {
        auditLogRepository.save(new AuditLog(
                auditLogRepository.nextId(),
                command.tenantId(),
                command.operatorId(),
                command.action(),
                command.resourceType(),
                command.resourceId(),
                LocalDateTime.now()
        ));
    }
}
