package com.callcenter.iam.infrastructure.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    @Test
    void shouldIncludeTenantAndAuthorizationClaimsInAccessToken() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "test-secret-key-for-iam-module-should-be-long-enough",
                Clock.fixed(Instant.parse("2026-06-04T09:00:00Z"), ZoneOffset.UTC),
                1800,
                604800
        );

        String token = provider.issueAccessToken(new JwtTokenProvider.TokenSubject(
                9L,
                1001L,
                List.of(1L, 2L),
                List.of(10L, 11L)
        ));

        JwtTokenProvider.TokenClaims claims = provider.parse(token);
        assertThat(claims.tenantId()).isEqualTo(9L);
        assertThat(claims.userId()).isEqualTo(1001L);
        assertThat(claims.roleIds()).containsExactly(1L, 2L);
        assertThat(claims.deptIds()).containsExactly(10L, 11L);
    }
}
