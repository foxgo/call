package com.callcenter.iam.interfaces.rest.tenant;

import com.callcenter.iam.application.tenant.CreateTenantCommand;
import com.callcenter.iam.application.tenant.CreateTenantUseCase;
import com.callcenter.iam.application.tenant.UpdateTenantUseCase;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.infrastructure.security.JwtAuthenticationFilter;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import(TenantControllerTest.TestSecurityConfig.class)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CreateTenantUseCase createTenantUseCase;

    @MockBean
    private UpdateTenantUseCase updateTenantUseCase;

    @MockBean
    private TenantRepository tenantRepository;

    @Test
    void shouldCreateTenant() throws Exception {
        when(createTenantUseCase.execute(any(CreateTenantCommand.class)))
                .thenReturn(Tenant.active(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0)));

        mockMvc.perform(post("/api/iam/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformAdminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantCode": "acme",
                                  "tenantName": "Acme",
                                  "expireTime": "2027-01-01T00:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9L))
                .andExpect(jsonPath("$.data.tenantCode").value("acme"))
                .andExpect(jsonPath("$.data.tenantName").value("Acme"));
    }

    @Test
    void shouldListTenants() throws Exception {
        when(tenantRepository.findAll()).thenReturn(List.of(
                Tenant.active(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0)),
                Tenant.active(10L, "globex", "Globex", LocalDateTime.of(2027, 6, 1, 0, 0))
        ));

        mockMvc.perform(get("/api/iam/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].tenantCode").value("acme"))
                .andExpect(jsonPath("$.data[1].tenantCode").value("globex"));
    }

    @Test
    void shouldRejectNonPlatformAdmin() throws Exception {
        mockMvc.perform(get("/api/iam/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isForbidden());
    }

    private String platformAdminToken() {
        return jwtTokenProvider.issueAccessToken(new JwtTokenProvider.TokenSubject(
                null,
                1L,
                List.of(),
                List.of()
        ));
    }

    private String tenantUserToken() {
        return jwtTokenProvider.issueAccessToken(new JwtTokenProvider.TokenSubject(
                9L,
                1001L,
                List.of(1L),
                List.of(10L)
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class TestSecurityConfig {

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
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
            return new JwtAuthenticationFilter(tokenProvider);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/iam/tenants/**").hasRole("PLATFORM_ADMIN")
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
