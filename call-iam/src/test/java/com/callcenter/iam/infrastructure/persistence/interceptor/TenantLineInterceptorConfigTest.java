package com.callcenter.iam.infrastructure.persistence.interceptor;

import com.callcenter.iam.application.authorization.ResolvedDataScope;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class TenantLineInterceptorConfigTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldResolveCurrentTenantIdForTenantUserQueries() {
        authenticateTenantUser(9L);
        bindRequest("/api/iam/users");
        TenantLineInterceptorConfig config = new TenantLineInterceptorConfig();

        Object expression = config.tenantLineHandler().getTenantId();

        assertThat(expression).isInstanceOf(LongValue.class);
        assertThat(((LongValue) expression).getValue()).isEqualTo(9L);
        assertThat(config.tenantLineHandler().ignoreTable("iam_user")).isFalse();
    }

    @Test
    void shouldBypassTenantFilteringForPlatformAdminEndpoints() {
        authenticatePlatformAdmin();
        bindRequest("/api/iam/tenants");
        TenantLineInterceptorConfig config = new TenantLineInterceptorConfig();

        assertThat(config.tenantLineHandler().ignoreTable("tenant")).isTrue();
        assertThat(config.tenantLineHandler().ignoreTable("iam_user")).isTrue();
    }

    @Test
    void shouldInjectDepartmentFilterIntoUserListQuery() {
        DataScopeQueryCustomizer customizer = new DataScopeQueryCustomizer();

        String sql = customizer.customizeUserListQuery(
                "SELECT * FROM iam_user",
                new ResolvedDataScope(false, List.of(20L, 21L), false, null)
        );

        assertThat(sql).contains("user_department");
        assertThat(sql).contains("department_id IN (20,21)");
        assertThat(sql).contains("SELECT * FROM iam_user WHERE");
    }

    @Test
    void shouldInjectSelfFilterIntoUserListQuery() {
        DataScopeQueryCustomizer customizer = new DataScopeQueryCustomizer();

        String sql = customizer.customizeUserListQuery(
                "SELECT * FROM iam_user",
                new ResolvedDataScope(false, List.of(), true, 1001L)
        );

        assertThat(sql).contains("id = 1001");
    }

    private void authenticateTenantUser(Long tenantId) {
        JwtTokenProvider.TokenClaims claims = new JwtTokenProvider.TokenClaims(
                tenantId,
                1001L,
                List.of(11L),
                List.of(20L),
                "access"
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                claims.userId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER"))
        );
        authenticationToken.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    private void authenticatePlatformAdmin() {
        JwtTokenProvider.TokenClaims claims = new JwtTokenProvider.TokenClaims(
                null,
                1L,
                List.of(),
                List.of(),
                "access"
        );
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                claims.userId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );
        authenticationToken.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
    }

    private void bindRequest(String uri) {
        HttpServletRequest request = new MockHttpServletRequest("GET", uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
