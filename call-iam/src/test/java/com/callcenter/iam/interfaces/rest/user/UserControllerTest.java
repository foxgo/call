package com.callcenter.iam.interfaces.rest.user;

import com.callcenter.iam.application.user.AssignUserDepartmentsUseCase;
import com.callcenter.iam.application.user.AssignUserRolesUseCase;
import com.callcenter.iam.application.user.CreateUserUseCase;
import com.callcenter.iam.application.user.DeleteUserUseCase;
import com.callcenter.iam.application.user.ResetUserPasswordUseCase;
import com.callcenter.iam.application.user.UpdateUserStatusUseCase;
import com.callcenter.iam.application.user.UpdateUserUseCase;
import com.callcenter.iam.application.user.UserAssignmentRepository;
import com.callcenter.iam.infrastructure.audit.AuditEventPublisher;
import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.organization.DepartmentRepository;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.domain.user.UserType;
import com.callcenter.iam.infrastructure.security.JwtAuthenticationFilter;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(UserControllerTest.TestUserConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserAssignmentRepository userAssignmentRepository;

    @MockBean
    private DepartmentRepository departmentRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateUser() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of());
        when(userRepository.findByTenantIdAndUsername(9L, "alice")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByTenantIdAndMobile(9L, "13800138000")).thenReturn(java.util.Optional.empty());
        when(userRepository.findByTenantIdAndEmail(9L, "alice@example.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode("Abcdef12")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAssignmentRepository.findRoleIds(1L)).thenReturn(List.of());
        when(userAssignmentRepository.findDepartmentIds(1L)).thenReturn(List.of());

        mockMvc.perform(post("/api/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "mobile": "13800138000",
                                  "email": "alice@example.com",
                                  "password": "Abcdef12",
                                  "nickname": "Alice"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.status").value("ENABLE"));
    }

    @Test
    void shouldRejectWeakPasswordWhenCreatingUser() throws Exception {
        mockMvc.perform(post("/api/iam/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "mobile": "13800138000",
                                  "email": "alice@example.com",
                                  "password": "weak",
                                  "nickname": "Alice"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldQueryUsersByDepartment() throws Exception {
        when(userRepository.findByTenantIdAndDepartmentId(9L, 20L)).thenReturn(List.of(
                User.createWithPasswordHash(1001L, 9L, UserType.TENANT, "alice", "13800138000", "alice@example.com", "encoded", "Alice")
        ));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of(11L));
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L));

        mockMvc.perform(get("/api/iam/users")
                        .param("departmentId", "20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].departmentIds[0]").value(20L));
    }

    @Test
    void shouldReturnCurrentUserProfile() throws Exception {
        when(userRepository.findById(1001L)).thenReturn(java.util.Optional.of(
                User.createWithPasswordHash(1001L, 9L, UserType.TENANT, "alice", "13800138000", "alice@example.com", "encoded", "Alice")
        ));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of(11L, 12L));
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L, 21L));

        mockMvc.perform(get("/api/iam/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1001L))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.tenantId").value(9L))
                .andExpect(jsonPath("$.data.roleIds[1]").value(12L))
                .andExpect(jsonPath("$.data.departmentIds[0]").value(20L));
    }

    @Test
    void shouldUpdateUserStatus() throws Exception {
        User user = User.createWithPasswordHash(1001L, 9L, UserType.TENANT, "alice", "13800138000", "alice@example.com", "encoded", "Alice");
        when(userRepository.findById(1001L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of());
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L));

        mockMvc.perform(put("/api/iam/users/1001/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISABLE"));
    }

    @Test
    void shouldResetPassword() throws Exception {
        User user = User.createWithPasswordHash(1001L, 9L, UserType.TENANT, "alice", "13800138000", "alice@example.com", "encoded", "Alice");
        when(userRepository.findById(1001L)).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.encode("Xyzabc12")).thenReturn("encoded-reset-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of());
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L));

        mockMvc.perform(put("/api/iam/users/1001/password/reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "Xyzabc12"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1001L));
    }

    @Test
    void shouldAssignUserRoles() throws Exception {
        User user = User.createWithPasswordHash(1001L, 9L, UserType.TENANT, "alice", "13800138000", "alice@example.com", "encoded", "Alice");
        when(userRepository.findById(1001L)).thenReturn(java.util.Optional.of(user));
        when(userAssignmentRepository.allRoleIdsBelongToTenant(9L, List.of(11L, 12L))).thenReturn(true);
        doNothing().when(userAssignmentRepository).replaceRoleIds(eq(9L), eq(1001L), eq(List.of(11L, 12L)));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of(11L, 12L));
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L));

        mockMvc.perform(put("/api/iam/users/1001/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleIds": [11, 12]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleIds.length()").value(2))
                .andExpect(jsonPath("$.data.roleIds[0]").value(11L));
    }

    @Test
    void shouldAssignUserDepartments() throws Exception {
        User user = User.createWithPasswordHash(1001L, 9L, UserType.TENANT, "alice", "13800138000", "alice@example.com", "encoded", "Alice");
        when(userRepository.findById(1001L)).thenReturn(java.util.Optional.of(user));
        when(departmentRepository.findById(20L)).thenReturn(java.util.Optional.of(new Department(20L, 9L, null, "Sales", "ACTIVE", 0)));
        when(departmentRepository.findById(21L)).thenReturn(java.util.Optional.of(new Department(21L, 9L, 20L, "Field", "ACTIVE", 10)));
        doNothing().when(userAssignmentRepository).replaceDepartmentIds(eq(9L), eq(1001L), eq(List.of(20L, 21L)));
        when(userAssignmentRepository.findRoleIds(1001L)).thenReturn(List.of(11L));
        when(userAssignmentRepository.findDepartmentIds(1001L)).thenReturn(List.of(20L, 21L));

        mockMvc.perform(put("/api/iam/users/1001/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentIds": [20, 21]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.departmentIds.length()").value(2))
                .andExpect(jsonPath("$.data.departmentIds[1]").value(21L));
    }

    private String tenantUserToken() {
        return jwtTokenProvider.issueAccessToken(new JwtTokenProvider.TokenSubject(
                9L,
                1001L,
                List.of(1L),
                List.of(20L)
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class TestUserConfig {

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
        AuditEventPublisher auditEventPublisher() {
            return command -> {
            };
        }

        @Bean
        CreateUserUseCase createUserUseCase(
                UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                AuditEventPublisher auditEventPublisher
        ) {
            return new CreateUserUseCase(userRepository, passwordEncoder, auditEventPublisher);
        }

        @Bean
        UpdateUserUseCase updateUserUseCase(UserRepository userRepository) {
            return new UpdateUserUseCase(userRepository);
        }

        @Bean
        DeleteUserUseCase deleteUserUseCase(UserRepository userRepository, UserAssignmentRepository userAssignmentRepository) {
            return new DeleteUserUseCase(userRepository, userAssignmentRepository);
        }

        @Bean
        UpdateUserStatusUseCase updateUserStatusUseCase(UserRepository userRepository) {
            return new UpdateUserStatusUseCase(userRepository);
        }

        @Bean
        ResetUserPasswordUseCase resetUserPasswordUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
            return new ResetUserPasswordUseCase(userRepository, passwordEncoder);
        }

        @Bean
        AssignUserRolesUseCase assignUserRolesUseCase(UserRepository userRepository, UserAssignmentRepository userAssignmentRepository) {
            return new AssignUserRolesUseCase(userRepository, userAssignmentRepository);
        }

        @Bean
        AssignUserDepartmentsUseCase assignUserDepartmentsUseCase(
                UserRepository userRepository,
                UserAssignmentRepository userAssignmentRepository,
                DepartmentRepository departmentRepository
        ) {
            return new AssignUserDepartmentsUseCase(userRepository, userAssignmentRepository, departmentRepository);
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/iam/users/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
