package com.callcenter.iam.application.tenant;

import java.time.LocalDateTime;

public record CreateTenantCommand(
        String tenantCode,
        String tenantName,
        LocalDateTime expireTime
) {
}
