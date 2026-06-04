package com.callcenter.iam.application.audit;

import com.callcenter.iam.application.auth.AuthenticationFailedException;
import com.callcenter.iam.application.auth.LoginCommand;
import com.callcenter.iam.application.auth.LoginUseCase;
import com.callcenter.iam.application.user.CreateUserCommand;
import com.callcenter.iam.application.user.CreateUserUseCase;
import com.callcenter.iam.domain.audit.AuditLog;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.infrastructure.audit.AuditEventPublisher;
import com.callcenter.iam.infrastructure.audit.AuditLogRepository;
import com.callcenter.iam.infrastructure.audit.AuditLogQuery;
import com.callcenter.iam.infrastructure.security.JwtAuthenticationFilter;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import com.callcenter.iam.infrastructure.security.RefreshTokenStore;
import com.callcenter.iam.interfaces.rest.audit.AuditLogController;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditPipelineTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            "test-secret-key-for-iam-module-should-be-long-enough",
            Clock.fixed(Instant.parse("2026-06-04T09:00:00Z"), ZoneOffset.UTC),
            1800,
            604800
    );

    @Test
    void shouldEmitAuditCommandWhenCreatingUser() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        CapturingAuditEventPublisher auditEventPublisher = new CapturingAuditEventPublisher();
        CreateUserUseCase useCase = new CreateUserUseCase(userRepository, passwordEncoder, auditEventPublisher);

        useCase.execute(new CreateUserCommand(
                9L,
                "alice",
                "13800138000",
                "alice@example.com",
                "Abcdef12",
                "Alice"
        ));

        assertThat(auditEventPublisher.commands).hasSize(1);
        AuditCommand command = auditEventPublisher.commands.getFirst();
        assertThat(command.action()).isEqualTo("USER_CREATED");
        assertThat(command.resourceType()).isEqualTo("USER");
        assertThat(command.resourceId()).isEqualTo("1");
        assertThat(command.tenantId()).isEqualTo(9L);
    }

    @Test
    void shouldEmitSuccessAndFailureAuditEventsForLogin() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryTenantRepository tenantRepository = new InMemoryTenantRepository();
        InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();
        CapturingAuditEventPublisher auditEventPublisher = new CapturingAuditEventPublisher();

        userRepository.save(User.createWithPasswordHash(
                1001L,
                9L,
                com.callcenter.iam.domain.user.UserType.TENANT,
                "tenant-admin",
                "13800138000",
                "tenant@example.com",
                passwordEncoder.encode("Abcdef12"),
                "Tenant Admin"
        ));
        tenantRepository.save(Tenant.active(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0)));

        LoginUseCase useCase = new LoginUseCase(
                userRepository,
                tenantRepository,
                passwordEncoder,
                tokenProvider,
                refreshTokenStore,
                auditEventPublisher
        );

        useCase.login(new LoginCommand(9L, "tenant-admin", "Abcdef12", List.of(11L), List.of(20L)));
        assertThrows(AuthenticationFailedException.class, () -> useCase.login(
                new LoginCommand(9L, "tenant-admin", "wrongPass1", List.of(11L), List.of(20L))
        ));

        assertThat(auditEventPublisher.commands).hasSize(2);
        assertThat(auditEventPublisher.commands.get(0).action()).isEqualTo("LOGIN_SUCCESS");
        assertThat(auditEventPublisher.commands.get(1).action()).isEqualTo("LOGIN_FAILURE");
    }

    @Test
    void shouldSupportOperatorAndResourceFiltersWhenQueryingAuditLogs() throws Exception {
        InMemoryAuditLogRepository auditLogRepository = new InMemoryAuditLogRepository();
        auditLogRepository.save(new AuditLog(1L, 9L, 1001L, "USER_CREATED", "USER", "1", LocalDateTime.of(2026, 6, 4, 9, 0)));
        auditLogRepository.save(new AuditLog(2L, 9L, 1002L, "USER_UPDATED", "USER", "2", LocalDateTime.of(2026, 6, 4, 9, 5)));
        auditLogRepository.save(new AuditLog(3L, 9L, 1001L, "ROLE_UPDATED", "ROLE", "11", LocalDateTime.of(2026, 6, 4, 9, 10)));

        AuditLogController controller = new AuditLogController(auditLogRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new JwtAuthenticationFilter(tokenProvider))
                .build();

        mockMvc.perform(get("/api/iam/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenantUserToken()))
                        .param("operatorId", "1001")
                        .param("resourceType", "USER")
                        .param("resourceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("USER_CREATED"))
                .andExpect(jsonPath("$.data[0].operatorId").value(1001L));
    }

    private String tenantUserToken() {
        return tokenProvider.issueAccessToken(new JwtTokenProvider.TokenSubject(
                9L,
                1001L,
                List.of(11L),
                List.of(20L)
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static final class CapturingAuditEventPublisher implements AuditEventPublisher {

        private final List<AuditCommand> commands = new ArrayList<>();

        @Override
        public void publish(AuditCommand command) {
            commands.add(command);
        }
    }

    private static final class InMemoryAuditLogRepository implements AuditLogRepository {

        private final List<AuditLog> storage = new ArrayList<>();

        @Override
        public AuditLog save(AuditLog auditLog) {
            storage.add(auditLog);
            return auditLog;
        }

        @Override
        public List<AuditLog> query(AuditLogQuery query) {
            return storage.stream()
                    .filter(log -> query.tenantId() == null || query.tenantId().equals(log.getTenantId()))
                    .filter(log -> query.operatorId() == null || query.operatorId().equals(log.getOperatorId()))
                    .filter(log -> query.resourceType() == null || query.resourceType().equals(log.getResourceType()))
                    .filter(log -> query.resourceId() == null || query.resourceId().equals(log.getResourceId()))
                    .toList();
        }

        @Override
        public Optional<AuditLog> findById(Long id) {
            return storage.stream().filter(log -> id.equals(log.getId())).findFirst();
        }

        @Override
        public long nextId() {
            return storage.stream()
                    .mapToLong(AuditLog::getId)
                    .max()
                    .orElse(0L) + 1;
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {

        private final Map<Long, User> storage = new HashMap<>();

        @Override
        public User save(User user) {
            storage.put(user.getId(), user);
            return user;
        }

        @Override
        public List<User> findAll() {
            return storage.values().stream().toList();
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public List<User> findByTenantId(Long tenantId) {
            return storage.values().stream().filter(user -> tenantId.equals(user.getTenantId())).toList();
        }

        @Override
        public List<User> findByTenantIdAndDepartmentId(Long tenantId, Long departmentId) {
            return List.of();
        }

        @Override
        public Optional<User> findByTenantIdAndUsername(Long tenantId, String username) {
            return storage.values().stream()
                    .filter(user -> tenantId.equals(user.getTenantId()) && username.equals(user.getUsername()))
                    .findFirst();
        }

        @Override
        public Optional<User> findByTenantIdAndMobile(Long tenantId, String mobile) {
            return storage.values().stream()
                    .filter(user -> tenantId.equals(user.getTenantId()) && mobile.equals(user.getMobile()))
                    .findFirst();
        }

        @Override
        public Optional<User> findByTenantIdAndEmail(Long tenantId, String email) {
            return storage.values().stream()
                    .filter(user -> tenantId.equals(user.getTenantId()) && email.equals(user.getEmail()))
                    .findFirst();
        }

        @Override
        public void deleteById(Long id) {
            storage.remove(id);
        }
    }

    private static final class InMemoryTenantRepository implements TenantRepository {

        private final Map<Long, Tenant> storage = new HashMap<>();

        @Override
        public Tenant save(Tenant tenant) {
            storage.put(tenant.getId(), tenant);
            return tenant;
        }

        @Override
        public Optional<Tenant> findById(Long id) {
            return Optional.ofNullable(storage.get(id));
        }

        @Override
        public Optional<Tenant> findByTenantCode(String tenantCode) {
            return storage.values().stream().filter(tenant -> tenantCode.equals(tenant.getTenantCode())).findFirst();
        }

        @Override
        public List<Tenant> findAll() {
            return storage.values().stream().toList();
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
