package com.callcenter.iam.application.user;

import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.organization.DepartmentRepository;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssignUserDepartmentsUseCase {

    private final UserRepository userRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final DepartmentRepository departmentRepository;

    public AssignUserDepartmentsUseCase(
            UserRepository userRepository,
            UserAssignmentRepository userAssignmentRepository,
            DepartmentRepository departmentRepository
    ) {
        this.userRepository = userRepository;
        this.userAssignmentRepository = userAssignmentRepository;
        this.departmentRepository = departmentRepository;
    }

    public User execute(AssignUserDepartmentsCommand command) {
        User user = requireTenantUser(command.tenantId(), command.userId());
        List<Long> departmentIds = command.departmentIds() == null ? List.of() : List.copyOf(command.departmentIds());
        for (Long departmentId : departmentIds) {
            Department department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new DomainRuleViolationException("department not found"));
            if (!command.tenantId().equals(department.getTenantId())) {
                throw new DomainRuleViolationException("department tenant mismatch");
            }
        }
        userAssignmentRepository.replaceDepartmentIds(command.tenantId(), command.userId(), departmentIds);
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
