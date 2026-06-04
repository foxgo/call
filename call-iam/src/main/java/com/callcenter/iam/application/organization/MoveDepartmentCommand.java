package com.callcenter.iam.application.organization;

public record MoveDepartmentCommand(
        Long tenantId,
        Long departmentId,
        Long newParentId
) {
}
