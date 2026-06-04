package com.callcenter.iam.interfaces.rest.authorization.response;

public record RoleDataScopeResponse(
        String scopeType,
        Long departmentId
) {
}
