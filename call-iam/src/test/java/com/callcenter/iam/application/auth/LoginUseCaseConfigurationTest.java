package com.callcenter.iam.application.auth;

import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.infrastructure.audit.AuditEventPublisher;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import com.callcenter.iam.infrastructure.security.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LoginUseCaseConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LoginUseCaseScanConfig.class, LoginUseCaseDependencyConfig.class);

    @Test
    void shouldRegisterLoginUseCaseAsSpringBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(LoginUseCase.class));
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = LoginUseCase.class)
    static class LoginUseCaseScanConfig {
    }

    @Configuration(proxyBeanMethods = false)
    static class LoginUseCaseDependencyConfig {

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        TenantRepository tenantRepository() {
            return mock(TenantRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return mock(JwtTokenProvider.class);
        }

        @Bean
        RefreshTokenStore refreshTokenStore() {
            return mock(RefreshTokenStore.class);
        }

        @Bean
        AuditEventPublisher auditEventPublisher() {
            return mock(AuditEventPublisher.class);
        }
    }
}
