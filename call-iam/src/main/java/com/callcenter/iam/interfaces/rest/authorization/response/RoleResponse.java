package com.callcenter.iam.interfaces.rest.authorization.response;

import java.util.List;

public record RoleResponse(
        Long id,
        String roleCode,
        String roleName,
        String roleType,
        List<Long> permissionIds,
        RoleDataScopeResponse dataScope
) {
}
