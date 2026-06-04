package com.callcenter.iam.domain.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

public class User {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private final Long id;
    private final Long tenantId;
    private final UserType userType;
    private final String username;
    private final String mobile;
    private final String email;
    private String passwordHash;
    private final String nickname;
    private UserStatus status;
    private LocalDateTime lastLoginTime;

    private User(
            Long id,
            Long tenantId,
            UserType userType,
            String username,
            String mobile,
            String email,
            String passwordHash,
            String nickname,
            UserStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userType = Objects.requireNonNull(userType, "userType must not be null");
        validateTenantOwnership(userType, tenantId);
        this.tenantId = tenantId;
        this.username = requireText(username, "username must not be blank");
        this.mobile = mobile;
        this.email = email;
        this.passwordHash = passwordPolicy(passwordHash);
        this.nickname = requireText(nickname, "nickname must not be blank");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static User create(
            Long id,
            Long tenantId,
            UserType userType,
            String username,
            String mobile,
            String email,
            String passwordHash,
            String nickname
    ) {
        return new User(id, tenantId, userType, username, mobile, email, passwordHash, nickname, UserStatus.ENABLE);
    }

    public static String passwordPolicy(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new DomainRuleViolationException("password must contain uppercase, lowercase, digit and have at least 8 chars");
        }
        return password;
    }

    public void lock() {
        this.status = UserStatus.LOCK;
    }

    public void disable() {
        this.status = UserStatus.DISABLE;
    }

    public void enable() {
        this.status = UserStatus.ENABLE;
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = passwordPolicy(newPasswordHash);
    }

    public void markLoggedIn(LocalDateTime loginTime) {
        this.lastLoginTime = Objects.requireNonNull(loginTime, "loginTime must not be null");
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public UserType getUserType() {
        return userType;
    }

    public String getUsername() {
        return username;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    private static void validateTenantOwnership(UserType userType, Long tenantId) {
        if (userType == UserType.PLATFORM) {
            return;
        }
        if (tenantId == null) {
            throw new DomainRuleViolationException("tenant user must have tenantId");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(message);
        }
        return value;
    }
}
