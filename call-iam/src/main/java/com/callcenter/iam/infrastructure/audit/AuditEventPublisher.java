package com.callcenter.iam.infrastructure.audit;

import com.callcenter.iam.application.audit.AuditCommand;

public interface AuditEventPublisher {

    void publish(AuditCommand command);
}
