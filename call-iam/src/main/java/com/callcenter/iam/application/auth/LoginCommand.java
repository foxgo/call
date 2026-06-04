package com.callcenter.iam.application.auth;

import java.util.List;

public record LoginCommand(
        Long tenantId,
        String identity,
        String password,
        List<Long> roleIds,
        List<Long> deptIds
) {
}
