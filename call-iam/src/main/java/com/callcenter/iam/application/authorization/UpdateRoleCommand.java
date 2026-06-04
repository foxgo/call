package com.callcenter.iam.application.authorization;

public record UpdateRoleCommand(
        Long tenantId,
        Long roleId,
        String roleCode,
        String roleName,
        String roleType
) {
}
