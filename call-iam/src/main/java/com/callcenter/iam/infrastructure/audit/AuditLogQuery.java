package com.callcenter.iam.infrastructure.audit;

public record AuditLogQuery(
        Long tenantId,
        Long operatorId,
        String resourceType,
        String resourceId
) {
}
