package com.callcenter.iam.interfaces.rest.authorization.response;

public record PermissionResponse(
        Long id,
        String permissionCode,
        String permissionName
) {
}
