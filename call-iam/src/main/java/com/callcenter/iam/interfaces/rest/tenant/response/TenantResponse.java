package com.callcenter.iam.interfaces.rest.tenant.response;

import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String tenantCode,
        String tenantName,
        String status,
        LocalDateTime expireTime
) {
}
