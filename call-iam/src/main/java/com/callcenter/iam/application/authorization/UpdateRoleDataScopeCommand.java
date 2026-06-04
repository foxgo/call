package com.callcenter.iam.application.authorization;

public record UpdateRoleDataScopeCommand(
        Long tenantId,
        Long roleId,
        String scopeType,
        Long departmentId
) {
}
