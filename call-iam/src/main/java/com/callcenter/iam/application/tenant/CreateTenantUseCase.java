package com.callcenter.iam.application.tenant;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateTenantUseCase {

    private final TenantRepository tenantRepository;

    public CreateTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant execute(CreateTenantCommand command) {
        tenantRepository.findByTenantCode(command.tenantCode()).ifPresent(existing -> {
            throw new DomainRuleViolationException("tenantCode already exists");
        });
        long id = tenantRepository.findAll().stream()
                .mapToLong(Tenant::getId)
                .max()
                .orElse(0L) + 1;
        Tenant tenant = Tenant.active(id, command.tenantCode(), command.tenantName(), command.expireTime());
        return tenantRepository.save(tenant);
    }
}
