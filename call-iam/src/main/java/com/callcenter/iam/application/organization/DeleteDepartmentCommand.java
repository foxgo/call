package com.callcenter.iam.application.organization;

public record DeleteDepartmentCommand(
        Long tenantId,
        Long departmentId
) {
}
