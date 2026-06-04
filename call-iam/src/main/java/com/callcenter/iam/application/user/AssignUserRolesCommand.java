package com.callcenter.iam.application.user;

import java.util.List;

public record AssignUserRolesCommand(
        Long tenantId,
        Long userId,
        List<Long> roleIds
) {
}
