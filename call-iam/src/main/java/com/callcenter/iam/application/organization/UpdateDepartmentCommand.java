package com.callcenter.iam.application.organization;

public record UpdateDepartmentCommand(
        Long tenantId,
        Long departmentId,
        String name,
        String status,
        int sort
) {
}
