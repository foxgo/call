package com.callcenter.iam.application.authorization;

import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import org.springframework.stereotype.Service;

@Service
public class DeleteRoleUseCase {

    private final AuthorizationRepository authorizationRepository;

    public DeleteRoleUseCase(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    public Role execute(DeleteRoleCommand command) {
        Role role = authorizationRepository.findRoleById(command.roleId())
                .orElseThrow(() -> new DomainRuleViolationException("role not found"));
        if (!command.tenantId().equals(role.getTenantId())) {
            throw new DomainRuleViolationException("role tenant mismatch");
        }
        authorizationRepository.deleteRoleById(command.roleId());
        return role;
    }
}
