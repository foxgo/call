package com.callcenter.iam.application.authorization;

public record CreateRoleCommand(
        Long tenantId,
        String roleCode,
        String roleName,
        String roleType
) {
}
