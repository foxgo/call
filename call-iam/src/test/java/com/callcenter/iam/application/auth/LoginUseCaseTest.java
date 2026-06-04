package com.callcenter.iam.application.auth;

import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

class LoginUseCaseTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            "test-secret-key-for-iam-module-should-be-long-enough",
            Clock.fixed(Instant.parse("2026-06-04T09:00:00Z"), ZoneOffset.UTC),
            1800,
            604800
    );

    @Test
    void shouldRejectLockedUser() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryTenantRepository tenantRepository = new InMemoryTenantRepository();
        InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();

        User locked = User.createWithPasswordHash(
                1001L,
                9L,
                com.callcenter.iam.domain.user.UserType.TENANT,
                "tenant-admin",
                "13800138000",
                "tenant@example.com",
                passwordEncoder.encode("Abcdef12"),
                "Tenant Admin"
        );
        locked.lock();
        userRepository.save(locked);
        tenantRepository.save(Tenant.active(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0)));

        LoginUseCase useCase = new LoginUseCase(userRepository, tenantRepository, passwordEncoder, tokenProvider, refreshTokenStore);

        assertThrows(AuthenticationFailedException.class, () -> useCase.login(new LoginCommand(
                9L,
                "tenant-admin",
                "Abcdef12",
                List.of(1L),
                List.of(10L)
        )));
    }

    @Test
    void shouldRejectSuspendedTenantUser() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryTenantRepository tenantRepository = new InMemoryTenantRepository();
        InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();

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
        tenantRepository.save(Tenant.suspended(9L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0)));

        LoginUseCase useCase = new LoginUseCase(userRepository, tenantRepository, passwordEncoder, tokenProvider, refreshTokenStore);

        assertThrows(AuthenticationFailedException.class, () -> useCase.login(new LoginCommand(
                9L,
                "tenant-admin",
                "Abcdef12",
                List.of(1L),
                List.of(10L)
        )));
    }

    @Test
    void shouldRotateRefreshTokenAndInvalidateOldToken() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryTenantRepository tenantRepository = new InMemoryTenantRepository();
        InMemoryRefreshTokenStore refreshTokenStore = new InMemoryRefreshTokenStore();

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

        LoginUseCase useCase = new LoginUseCase(userRepository, tenantRepository, passwordEncoder, tokenProvider, refreshTokenStore);

        LoginResult loginResult = useCase.login(new LoginCommand(
                9L,
                "tenant-admin",
                "Abcdef12",
                List.of(1L, 2L),
                List.of(10L, 11L)
        ));

        LoginResult rotated = useCase.refresh(loginResult.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(loginResult.refreshToken());
        assertThrows(AuthenticationFailedException.class, () -> useCase.refresh(loginResult.refreshToken()));
        assertThat(refreshTokenStore.contains(rotated.refreshToken())).isTrue();
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
            return storage.values().stream()
                    .filter(user -> tenantId.equals(user.getTenantId()))
                    .toList();
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
            return storage.values().stream()
                    .filter(tenant -> tenantCode.equals(tenant.getTenantCode()))
                    .findFirst();
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
