package com.callcenter.iam.infrastructure.security;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenStore {

    void store(String refreshToken, JwtTokenProvider.TokenSubject subject, Instant expiresAt);

    Optional<JwtTokenProvider.TokenSubject> consume(String refreshToken);

    boolean contains(String refreshToken);
}
