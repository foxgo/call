package com.callcenter.iam.application.organization;

public record CreateDepartmentCommand(
        Long id,
        Long tenantId,
        Long parentId,
        String name,
        String status,
        int sort
) {
}
