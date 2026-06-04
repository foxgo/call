package com.callcenter.iam.domain.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @Test
    void shouldRejectInvalidPasswordFormat() {
        assertThrows(DomainRuleViolationException.class, () -> User.passwordPolicy("password"));
    }

    @Test
    void platformUserCanHaveNullTenantId() {
        assertDoesNotThrow(() -> User.create(
                1L,
                null,
                UserType.PLATFORM,
                "platform-admin",
                "13800138000",
                "platform@example.com",
                "Abcdef12",
                "Platform Admin"
        ));
    }

    @Test
    void tenantUserMustHaveNonNullTenantId() {
        assertThrows(DomainRuleViolationException.class, () -> User.create(
                2L,
                null,
                UserType.TENANT,
                "tenant-admin",
                "13900139000",
                "tenant@example.com",
                "Abcdef12",
                "Tenant Admin"
        ));
    }
}
