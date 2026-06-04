package com.callcenter.iam.application.tenant;

import java.time.LocalDateTime;

public record UpdateTenantCommand(
        Long tenantId,
        String tenantName,
        LocalDateTime expireTime
) {
}
