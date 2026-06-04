package com.callcenter.iam.interfaces.rest.authorization;

import com.callcenter.iam.application.authorization.AuthorizationRepository;
import com.callcenter.iam.application.authorization.CreateRoleUseCase;
import com.callcenter.iam.application.authorization.DeleteRoleUseCase;
import com.callcenter.iam.application.authorization.UpdateRoleDataScopeUseCase;
import com.callcenter.iam.application.authorization.UpdateRolePermissionsUseCase;
import com.callcenter.iam.application.authorization.UpdateRoleUseCase;
import com.callcenter.iam.domain.authorization.Permission;
import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.infrastructure.security.JwtAuthenticationFilter;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({RoleController.class, PermissionController.class})
@Import(RoleControllerTest.TestAuthorizationConfig.class)
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthorizationRepository authorizationRepository;

    @Test
    void shouldCreateRole() throws Exception {
        when(authorizationRepository.existsRoleCode(9L, "tenant-admin")).thenReturn(false);
        when(authorizationRepository.findRolesByTenantId(9L)).thenReturn(List.of());
        when(authorizationRepository.saveRole(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationRepository.findPermissionIdsByRoleId(1L)).thenReturn(List.of());
        when(authorizationRepository.findRoleDataScopes(9L, List.of(1L))).thenReturn(List.of());

        mockMvc.perform(post("/api/iam/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleCode": "tenant-admin",
                                  "roleName": "Tenant Admin",
                                  "roleType": "TENANT_CUSTOM"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.roleCode").value("tenant-admin"));
    }

    @Test
    void shouldUpdateRolePermissions() throws Exception {
        when(authorizationRepository.findRoleById(11L))
                .thenReturn(Optional.of(new Role(11L, 9L, "tenant-admin", "Tenant Admin", "TENANT_CUSTOM")));
        when(authorizationRepository.allPermissionIdsExist(List.of(101L, 102L))).thenReturn(true);
        doNothing().when(authorizationRepository).replaceRolePermissions(9L, 11L, List.of(101L, 102L));
        when(authorizationRepository.findPermissionIdsByRoleId(11L)).thenReturn(List.of(101L, 102L));
        when(authorizationRepository.findRoleDataScopes(9L, List.of(11L))).thenReturn(List.of());

        mockMvc.perform(put("/api/iam/roles/11/permissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissionIds": [101, 102]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.permissionIds.length()").value(2))
                .andExpect(jsonPath("$.data.permissionIds[1]").value(102L));
    }

    @Test
    void shouldUpdateRoleDataScope() throws Exception {
        when(authorizationRepository.findRoleById(11L))
                .thenReturn(Optional.of(new Role(11L, 9L, "tenant-admin", "Tenant Admin", "TENANT_CUSTOM")));
        doNothing().when(authorizationRepository).replaceRoleDataScope(9L, 11L, "DEPARTMENT_AND_CHILD", 20L);
        when(authorizationRepository.findPermissionIdsByRoleId(11L)).thenReturn(List.of(101L));
        when(authorizationRepository.findRoleDataScopes(9L, List.of(11L)))
                .thenReturn(List.of(new com.callcenter.iam.application.authorization.RoleDataScope(11L, 9L, "DEPARTMENT_AND_CHILD", 20L)));

        mockMvc.perform(put("/api/iam/roles/11/data-scope")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scopeType": "DEPARTMENT_AND_CHILD",
                                  "departmentId": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dataScope.scopeType").value("DEPARTMENT_AND_CHILD"))
                .andExpect(jsonPath("$.data.dataScope.departmentId").value(20L));
    }

    @Test
    void shouldListPermissions() throws Exception {
        when(authorizationRepository.findAllPermissions()).thenReturn(List.of(
                new Permission(101L, "iam:user:create", "Create User"),
                new Permission(102L, "iam:user:update", "Update User")
        ));

        mockMvc.perform(get("/api/iam/permissions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].permissionCode").value("iam:user:create"));
    }

    private String tenantUserToken() {
        return jwtTokenProvider.issueAccessToken(new JwtTokenProvider.TokenSubject(
                9L,
                1001L,
                List.of(11L),
                List.of(20L)
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class TestAuthorizationConfig {

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
        CreateRoleUseCase createRoleUseCase(AuthorizationRepository authorizationRepository) {
            return new CreateRoleUseCase(authorizationRepository);
        }

        @Bean
        UpdateRoleUseCase updateRoleUseCase(AuthorizationRepository authorizationRepository) {
            return new UpdateRoleUseCase(authorizationRepository);
        }

        @Bean
        DeleteRoleUseCase deleteRoleUseCase(AuthorizationRepository authorizationRepository) {
            return new DeleteRoleUseCase(authorizationRepository);
        }

        @Bean
        UpdateRolePermissionsUseCase updateRolePermissionsUseCase(AuthorizationRepository authorizationRepository) {
            return new UpdateRolePermissionsUseCase(authorizationRepository);
        }

        @Bean
        UpdateRoleDataScopeUseCase updateRoleDataScopeUseCase(AuthorizationRepository authorizationRepository) {
            return new UpdateRoleDataScopeUseCase(authorizationRepository);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/iam/roles/**", "/api/iam/permissions/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
