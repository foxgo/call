package com.callcenter.iam.application.audit;

public record AuditCommand(
        Long tenantId,
        Long operatorId,
        String action,
        String resourceType,
        String resourceId
) {
}
