package com.callcenter.iam.application.user;

public record ResetUserPasswordCommand(
        Long tenantId,
        Long userId,
        String newPassword
) {
}
