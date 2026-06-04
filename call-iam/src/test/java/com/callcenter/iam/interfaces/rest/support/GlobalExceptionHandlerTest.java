package com.callcenter.iam.interfaces.rest.support;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.infrastructure.security.JwtAuthenticationFilter;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({
        GlobalExceptionHandlerTest.TestSupportConfig.class,
        OpenApiConfiguration.class
})
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        MultipleOpenApiSupportConfiguration.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void shouldMapValidationErrorsToBadRequest() throws Exception {
        mockMvc.perform(post("/probe/validate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldMapForbiddenErrorsToForbidden() throws Exception {
        mockMvc.perform(get("/probe/admin-only")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldMapDuplicateDataToConflict() throws Exception {
        mockMvc.perform(get("/probe/conflict")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldExposeOpenApiEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
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

    @Validated
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody ProbeRequest request) {
            return request.name();
        }

        @GetMapping("/admin-only")
        public String adminOnly() {
            return "ok";
        }

        @GetMapping("/conflict")
        public String conflict() {
            throw new DomainRuleViolationException("duplicate data");
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }

    @TestConfiguration
    static class TestSupportConfig {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

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
                            .requestMatchers("/v3/api-docs/**").permitAll()
                            .requestMatchers("/probe/admin-only").hasRole("PLATFORM_ADMIN")
                            .anyRequest().authenticated()
                    )
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }
    }
}
