package com.callcenter.iam.application.authorization;

import java.util.List;

public record UpdateRolePermissionsCommand(
        Long tenantId,
        Long roleId,
        List<Long> permissionIds
) {
}
