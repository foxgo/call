package com.callcenter.iam.application.authorization;

import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import org.springframework.stereotype.Service;

@Service
public class CreateRoleUseCase {

    private final AuthorizationRepository authorizationRepository;

    public CreateRoleUseCase(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    public Role execute(CreateRoleCommand command) {
        if (authorizationRepository.existsRoleCode(command.tenantId(), command.roleCode())) {
            throw new DomainRuleViolationException("roleCode already exists");
        }
        long roleId = authorizationRepository.findRolesByTenantId(command.tenantId()).stream()
                .mapToLong(Role::getId)
                .max()
                .orElse(0L) + 1;
        return authorizationRepository.saveRole(new Role(
                roleId,
                command.tenantId(),
                command.roleCode(),
                command.roleName(),
                command.roleType()
        ));
    }
}
