package com.callcenter.iam.domain.tenant;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantTest {

    @Test
    void shouldRejectReactivateDeletedTenant() {
        Tenant tenant = Tenant.deleted(1L, "acme", "Acme");

        assertThrows(DomainRuleViolationException.class, tenant::reactivate);
    }

    @Test
    void shouldReactivateSuspendedTenant() {
        Tenant tenant = Tenant.suspended(1L, "acme", "Acme", LocalDateTime.of(2027, 1, 1, 0, 0));

        tenant.reactivate();
    }
}
