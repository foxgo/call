package com.callcenter.iam.application.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssignUserRolesUseCase {

    private final UserRepository userRepository;
    private final UserAssignmentRepository userAssignmentRepository;

    public AssignUserRolesUseCase(UserRepository userRepository, UserAssignmentRepository userAssignmentRepository) {
        this.userRepository = userRepository;
        this.userAssignmentRepository = userAssignmentRepository;
    }

    public User execute(AssignUserRolesCommand command) {
        User user = requireTenantUser(command.tenantId(), command.userId());
        List<Long> roleIds = command.roleIds() == null ? List.of() : List.copyOf(command.roleIds());
        if (!userAssignmentRepository.allRoleIdsBelongToTenant(command.tenantId(), roleIds)) {
            throw new DomainRuleViolationException("role tenant mismatch");
        }
        userAssignmentRepository.replaceRoleIds(command.tenantId(), command.userId(), roleIds);
        return user;
    }

    private User requireTenantUser(Long tenantId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainRuleViolationException("user not found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new DomainRuleViolationException("user tenant mismatch");
        }
        return user;
    }
}
