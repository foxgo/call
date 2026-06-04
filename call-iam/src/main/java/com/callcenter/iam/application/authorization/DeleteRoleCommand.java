package com.callcenter.iam.application.authorization;

public record DeleteRoleCommand(
        Long tenantId,
        Long roleId
) {
}
