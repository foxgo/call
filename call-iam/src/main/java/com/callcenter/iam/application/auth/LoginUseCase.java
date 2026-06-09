package com.callcenter.iam.application.auth;

import com.callcenter.iam.application.audit.AuditCommand;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.tenant.TenantStatus;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.domain.user.UserStatus;
import com.callcenter.iam.domain.user.UserType;
import com.callcenter.iam.infrastructure.audit.AuditEventPublisher;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import com.callcenter.iam.infrastructure.security.RefreshTokenStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AuditEventPublisher auditEventPublisher;

    public LoginUseCase(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore,
            AuditEventPublisher auditEventPublisher
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.auditEventPublisher = auditEventPublisher;
    }

    public LoginResult login(LoginCommand command) {
        User user = findUser(command.tenantId(), command.identity()).orElse(null);
        try {
            if (user == null || !passwordEncoder.matches(command.password(), user.getPasswordHash())) {
                throw new AuthenticationFailedException("invalid credentials");
            }
            ensureUserCanLogin(user);

            JwtTokenProvider.TokenSubject subject = new JwtTokenProvider.TokenSubject(
                    user.getTenantId(),
                    user.getId(),
                    safeList(command.roleIds()),
                    safeList(command.deptIds())
            );
            LoginResult result = issueTokens(subject);
            auditEventPublisher.publish(new AuditCommand(
                    command.tenantId(),
                    user.getId(),
                    "LOGIN_SUCCESS",
                    "USER",
                    String.valueOf(user.getId())
            ));
            return result;
        } catch (AuthenticationFailedException ex) {
            auditEventPublisher.publish(new AuditCommand(
                    command.tenantId(),
                    user == null ? null : user.getId(),
                    "LOGIN_FAILURE",
                    "USER",
                    user == null ? command.identity() : String.valueOf(user.getId())
            ));
            throw ex;
        }
    }

    public LoginResult refresh(String refreshToken) {
        JwtTokenProvider.TokenClaims claims = tokenProvider.parse(refreshToken);
        if (!"refresh".equals(claims.type())) {
            throw new AuthenticationFailedException("invalid refresh token type");
        }
        JwtTokenProvider.TokenSubject subject = refreshTokenStore.consume(refreshToken)
                .orElseThrow(() -> new AuthenticationFailedException("refresh token expired or invalid"));
        if (!subject.userId().equals(claims.userId())) {
            throw new AuthenticationFailedException("refresh token subject mismatch");
        }
        return issueTokens(subject);
    }

    private LoginResult issueTokens(JwtTokenProvider.TokenSubject subject) {
        String accessToken = tokenProvider.issueAccessToken(subject);
        String refreshToken = tokenProvider.issueRefreshToken(subject);
        refreshTokenStore.store(refreshToken, subject, Instant.now().plusSeconds(tokenProvider.refreshTokenTtlSeconds()));
        return new LoginResult(accessToken, refreshToken);
    }

    private Optional<User> findUser(Long tenantId, String identity) {
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }
        if (identity.contains("@")) {
            return userRepository.findByTenantIdAndEmail(tenantId, identity);
        }
        if (identity.chars().allMatch(Character::isDigit)) {
            return userRepository.findByTenantIdAndMobile(tenantId, identity);
        }
        return userRepository.findByTenantIdAndUsername(tenantId, identity);
    }

    private void ensureUserCanLogin(User user) {
        if (user.getStatus() == UserStatus.LOCK || user.getStatus() == UserStatus.DISABLE) {
            throw new AuthenticationFailedException("user is not allowed to login");
        }
        if (user.getUserType() == UserType.PLATFORM) {
            return;
        }
        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new AuthenticationFailedException("tenant not found"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new AuthenticationFailedException("tenant is not active");
        }
    }

    private List<Long> safeList(List<Long> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
