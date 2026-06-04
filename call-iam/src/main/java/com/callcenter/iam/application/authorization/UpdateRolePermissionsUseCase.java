package com.callcenter.iam.application.authorization;

import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UpdateRolePermissionsUseCase {

    private final AuthorizationRepository authorizationRepository;

    public UpdateRolePermissionsUseCase(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    public Role execute(UpdateRolePermissionsCommand command) {
        Role role = requireRole(command.tenantId(), command.roleId());
        List<Long> permissionIds = command.permissionIds() == null ? List.of() : List.copyOf(command.permissionIds());
        if (!authorizationRepository.allPermissionIdsExist(permissionIds)) {
            throw new DomainRuleViolationException("permission not found");
        }
        authorizationRepository.replaceRolePermissions(command.tenantId(), command.roleId(), permissionIds);
        return role;
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
