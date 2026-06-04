package com.callcenter.iam.application.user;

public record CreateUserCommand(
        Long tenantId,
        String username,
        String mobile,
        String email,
        String password,
        String nickname
) {
}
