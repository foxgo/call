package com.callcenter.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.callcenter.observability.logging.StructuredLogFields;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPopulateUserAndTenantIntoMdcWhenTokenIsValid() throws Exception {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(
                "secret-key",
                Clock.fixed(Instant.parse("2026-06-09T10:00:00Z"), ZoneOffset.UTC),
                3600,
                7200
        );
        String token = tokenProvider.issueAccessToken(new JwtTokenProvider.TokenSubject(9L, 1001L, List.of(7L), List.of(1L)));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> tenantId = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            userId.set(MDC.get(StructuredLogFields.USER_ID));
            tenantId.set(MDC.get(StructuredLogFields.TENANT_ID));
        };

        filter.doFilter(request, response, chain);

        assertThat(userId).hasValue("1001");
        assertThat(tenantId).hasValue("9");
        assertThat(MDC.get(StructuredLogFields.USER_ID)).isNull();
        assertThat(MDC.get(StructuredLogFields.TENANT_ID)).isNull();
    }

    @Test
    void shouldNotLeaveStaleMdcWhenTokenIsInvalid() throws Exception {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(
                "secret-key",
                Clock.fixed(Instant.parse("2026-06-09T10:00:00Z"), ZoneOffset.UTC),
                3600,
                7200
        );
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenProvider);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> userId = new AtomicReference<>("present");
        FilterChain chain = (servletRequest, servletResponse) -> userId.set(MDC.get(StructuredLogFields.USER_ID));

        filter.doFilter(request, response, chain);

        assertThat(userId.get()).isNull();
        assertThat(MDC.get(StructuredLogFields.USER_ID)).isNull();
    }
}
