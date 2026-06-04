package com.callcenter.iam.interfaces.rest.organization;

import com.callcenter.iam.application.organization.CreateDepartmentCommand;
import com.callcenter.iam.application.organization.CreateDepartmentUseCase;
import com.callcenter.iam.application.organization.DeleteDepartmentUseCase;
import com.callcenter.iam.application.organization.MoveDepartmentCommand;
import com.callcenter.iam.application.organization.MoveDepartmentUseCase;
import com.callcenter.iam.application.organization.UpdateDepartmentCommand;
import com.callcenter.iam.application.organization.UpdateDepartmentUseCase;
import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.organization.DepartmentRepository;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@Import(DepartmentControllerTest.TestSecurityConfig.class)
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CreateDepartmentUseCase createDepartmentUseCase;

    @MockBean
    private UpdateDepartmentUseCase updateDepartmentUseCase;

    @MockBean
    private MoveDepartmentUseCase moveDepartmentUseCase;

    @MockBean
    private DeleteDepartmentUseCase deleteDepartmentUseCase;

    @MockBean
    private DepartmentRepository departmentRepository;

    @Test
    void shouldReturnNestedDepartmentTree() throws Exception {
        when(departmentRepository.findByTenantId(9L)).thenReturn(List.of(
                new Department(1L, 9L, null, "Root", "ACTIVE", 0),
                new Department(2L, 9L, 1L, "Sales", "ACTIVE", 10),
                new Department(3L, 9L, 2L, "Field", "ACTIVE", 20)
        ));

        mockMvc.perform(get("/api/iam/departments/tree")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Root"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("Sales"))
                .andExpect(jsonPath("$.data[0].children[0].children[0].name").value("Field"));
    }

    @Test
    void shouldCreateDepartment() throws Exception {
        when(createDepartmentUseCase.execute(any(CreateDepartmentCommand.class)))
                .thenReturn(new Department(4L, 9L, 1L, "Support", "ACTIVE", 30));

        mockMvc.perform(post("/api/iam/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 1,
                                  "name": "Support",
                                  "status": "ACTIVE",
                                  "sort": 30
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(4L))
                .andExpect(jsonPath("$.data.parentId").value(1L))
                .andExpect(jsonPath("$.data.name").value("Support"));
    }

    @Test
    void shouldUpdateDepartment() throws Exception {
        when(updateDepartmentUseCase.execute(any(UpdateDepartmentCommand.class)))
                .thenReturn(new Department(2L, 9L, 1L, "Revenue", "ACTIVE", 15));

        mockMvc.perform(put("/api/iam/departments/2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Revenue",
                                  "status": "ACTIVE",
                                  "sort": 15
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Revenue"))
                .andExpect(jsonPath("$.data.sort").value(15));
    }

    @Test
    void shouldMoveDepartment() throws Exception {
        when(moveDepartmentUseCase.execute(any(MoveDepartmentCommand.class)))
                .thenReturn(new Department(3L, 9L, 1L, "Field", "ACTIVE", 20));

        mockMvc.perform(put("/api/iam/departments/3/move")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.parentId").value(1L))
                .andExpect(jsonPath("$.data.name").value("Field"));
    }

    @Test
    void shouldDeleteDepartment() throws Exception {
        when(deleteDepartmentUseCase.execute(any()))
                .thenReturn(new Department(3L, 9L, 2L, "Field", "ACTIVE", 20));

        mockMvc.perform(delete("/api/iam/departments/3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(3L))
                .andExpect(jsonPath("$.data.name").value("Field"));
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
                            .requestMatchers("/api/iam/departments/**").authenticated()
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
