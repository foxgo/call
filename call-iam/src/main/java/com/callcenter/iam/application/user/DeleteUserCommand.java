package com.callcenter.iam.application.user;

public record DeleteUserCommand(
        Long tenantId,
        Long userId
) {
}
