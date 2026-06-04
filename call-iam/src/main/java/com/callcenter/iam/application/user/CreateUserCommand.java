package com.callcenter.iam.application.user;

public record CreateUserCommand(
        Long tenantId,
        String username,
        String mobile,
        String email,
        String password,
        String nickname,
        Long operatorId
) {
    public CreateUserCommand(
            Long tenantId,
            String username,
            String mobile,
            String email,
            String password,
            String nickname
    ) {
        this(tenantId, username, mobile, email, password, nickname, null);
    }
}
