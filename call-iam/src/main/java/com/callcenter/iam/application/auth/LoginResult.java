package com.callcenter.iam.application.auth;

public record LoginResult(
        String accessToken,
        String refreshToken
) {
}
