package com.callcenter.iam.domain.tenant;

import java.util.Optional;

public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(Long id);

    Optional<Tenant> findByTenantCode(String tenantCode);
}
