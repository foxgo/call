package com.callcenter.iam.application.authorization;

import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpdateRoleUseCase {

    private final AuthorizationRepository authorizationRepository;

    public UpdateRoleUseCase(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    public Role execute(UpdateRoleCommand command) {
        Role role = requireRole(command.tenantId(), command.roleId());
        if (!Objects.equals(role.getRoleCode(), command.roleCode())
                && authorizationRepository.existsRoleCode(command.tenantId(), command.roleCode())) {
            throw new DomainRuleViolationException("roleCode already exists");
        }
        role.updateBasics(command.roleCode(), command.roleName(), command.roleType());
        return authorizationRepository.saveRole(role);
    }

    private Role requireRole(Long tenantId, Long roleId) {
        Role role = authorizationRepository.findRoleById(roleId)
                .orElseThrow(() -> new DomainRuleViolationException("role not found"));
        if (!tenantId.equals(role.getTenantId())) {
            throw new DomainRuleViolationException("role tenant mismatch");
        }
        return role;
    }
}
