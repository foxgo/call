package com.callcenter.iam.interfaces.rest.auth;

import com.callcenter.iam.application.auth.LoginUseCase;
import com.callcenter.iam.application.user.UserAssignmentRepository;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.domain.user.UserType;
import com.callcenter.iam.infrastructure.audit.AuditEventPublisher;
import com.callcenter.iam.infrastructure.security.JwtAuthenticationFilter;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import com.callcenter.iam.infrastructure.security.RefreshTokenStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(AuthControllerTest.TestAuthConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private UserAssignmentRepository userAssignmentRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldLoginWithTenantCodeAndReturnTokens() throws Exception {
        User user = User.createWithPasswordHash(
                1001L,
                9L,
                UserType.TENANT,
                "tenant-admin",
                "13800138000",
                "tenant@example.com",
                "encoded-password",
                "Tenant Admin"
        );
        Tenant tenant = Tenant.active(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0));

        when(tenantRepository.findByTenantCode("acme")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantIdAndUsername(9L, "tenant-admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abcdef12", "encoded-password")).thenReturn(true);
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of(11L, 12L));
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L, 21L));

        mockMvc.perform(post("/api/iam/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantCode": "acme",
                                  "account": "tenant-admin",
                                  "password": "Abcdef12"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString());
    }

    @Test
    void shouldRefreshToken() throws Exception {
        User user = User.createWithPasswordHash(
                1001L,
                9L,
                UserType.TENANT,
                "tenant-admin",
                "13800138000",
                "tenant@example.com",
                "encoded-password",
                "Tenant Admin"
        );
        Tenant tenant = Tenant.active(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0));

        when(tenantRepository.findByTenantCode("acme")).thenReturn(Optional.of(tenant));
        when(userRepository.findByTenantIdAndUsername(9L, "tenant-admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Abcdef12", "encoded-password")).thenReturn(true);
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of(11L));
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L));

        String loginResponse = mockMvc.perform(post("/api/iam/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantCode": "acme",
                                  "account": "tenant-admin",
                                  "password": "Abcdef12"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = loginResponse.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        String refreshResponse = mockMvc.perform(post("/api/iam/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedToken = refreshResponse.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");
        assertThat(rotatedToken).isNotEqualTo(refreshToken);
        assertThat(refreshTokenStore.contains(rotatedToken)).isTrue();
    }

    @TestConfiguration
    static class TestAuthConfig {

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(
                    "test-secret-key-for-iam-module-should-be-long-enough",
                    Clock.fixed(Instant.parse("2026-06-04T09:00:00Z"), ZoneOffset.UTC),
                    1800,
                    604800
            );
        }

        @Bean
        RefreshTokenStore refreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }

        @Bean
        AuditEventPublisher auditEventPublisher() {
            return command -> {
            };
        }

        @Bean
        LoginUseCase loginUseCase(
                UserRepository userRepository,
                TenantRepository tenantRepository,
                PasswordEncoder passwordEncoder,
                JwtTokenProvider jwtTokenProvider,
                RefreshTokenStore refreshTokenStore,
                AuditEventPublisher auditEventPublisher
        ) {
            return new LoginUseCase(
                    userRepository,
                    tenantRepository,
                    passwordEncoder,
                    jwtTokenProvider,
                    refreshTokenStore,
                    auditEventPublisher
            );
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
            return new JwtAuthenticationFilter(tokenProvider);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/iam/auth/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }

    private static final class InMemoryRefreshTokenStore implements RefreshTokenStore {

        private final Map<String, JwtTokenProvider.TokenSubject> storage = new HashMap<>();

        @Override
        public void store(String refreshToken, JwtTokenProvider.TokenSubject subject, Instant expiresAt) {
            storage.put(refreshToken, subject);
        }

        @Override
        public Optional<JwtTokenProvider.TokenSubject> consume(String refreshToken) {
            return Optional.ofNullable(storage.remove(refreshToken));
        }

        @Override
        public boolean contains(String refreshToken) {
            return storage.containsKey(refreshToken);
        }
    }
}
