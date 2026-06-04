package com.callcenter.iam.infrastructure.persistence.interceptor;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class TenantLineInterceptorConfig {

    private static final Set<String> PLATFORM_TABLES = Set.of("tenant", "permission");

    @Bean
    public MybatisPlusInterceptor iamMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        return interceptor;
    }

    TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = currentClaims() == null ? null : currentClaims().tenantId();
                return new LongValue(tenantId == null ? -1L : tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (PLATFORM_TABLES.contains(tableName)) {
                    return true;
                }
                if (currentClaims() == null || currentClaims().tenantId() == null) {
                    return true;
                }
                return isPlatformTenantEndpoint();
            }
        };
    }

    private boolean isPlatformTenantEndpoint() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        return request != null && request.getRequestURI() != null && request.getRequestURI().startsWith("/api/iam/tenants");
    }

    private JwtTokenProvider.TokenClaims currentClaims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof JwtTokenProvider.TokenClaims claims)) {
            return null;
        }
        return claims;
    }
}
