package com.callcenter.iam.application.tenant;

import com.callcenter.iam.application.auth.AuthenticationFailedException;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateTenantUseCase {

    private final TenantRepository tenantRepository;

    public UpdateTenantUseCase(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant execute(UpdateTenantCommand command) {
        Tenant tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new AuthenticationFailedException("tenant not found"));
        tenant.updateBasics(command.tenantName(), command.expireTime());
        return tenantRepository.save(tenant);
    }
}
