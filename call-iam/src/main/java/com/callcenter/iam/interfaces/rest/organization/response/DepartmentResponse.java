package com.callcenter.iam.interfaces.rest.organization.response;

public record DepartmentResponse(
        Long id,
        Long parentId,
        String name,
        String status,
        int sort
) {
}
