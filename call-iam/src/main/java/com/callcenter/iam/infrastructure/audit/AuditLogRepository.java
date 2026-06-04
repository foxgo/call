package com.callcenter.iam.infrastructure.audit;

import com.callcenter.iam.domain.audit.AuditLog;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> query(AuditLogQuery query);

    Optional<AuditLog> findById(Long id);

    long nextId();
}
