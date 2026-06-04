package com.callcenter.iam.application.user;

public record UpdateUserCommand(
        Long tenantId,
        Long userId,
        String mobile,
        String email,
        String nickname
) {
}
