package com.callcenter.iam.application.authorization;

public record RoleDataScope(
        Long roleId,
        Long tenantId,
        String scopeType,
        Long departmentId
) {
}
