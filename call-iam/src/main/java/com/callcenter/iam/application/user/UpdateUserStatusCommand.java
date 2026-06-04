package com.callcenter.iam.application.user;

public record UpdateUserStatusCommand(
        Long tenantId,
        Long userId,
        String status
) {
}
