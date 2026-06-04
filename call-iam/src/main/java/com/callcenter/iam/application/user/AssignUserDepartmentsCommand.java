package com.callcenter.iam.application.user;

import java.util.List;

public record AssignUserDepartmentsCommand(
        Long tenantId,
        Long userId,
        List<Long> departmentIds
) {
}
