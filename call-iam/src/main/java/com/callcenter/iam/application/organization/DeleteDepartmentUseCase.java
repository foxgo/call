package com.callcenter.iam.application.organization;

import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DeleteDepartmentUseCase {

    private final DepartmentTreeRepository repository;

    public DeleteDepartmentUseCase(DepartmentTreeRepository repository) {
        this.repository = repository;
    }

    public Department execute(DeleteDepartmentCommand command) {
        Department department = repository.findById(command.departmentId())
                .orElseThrow(() -> new DomainRuleViolationException("department not found"));
        if (!command.tenantId().equals(department.getTenantId())) {
            throw new DomainRuleViolationException("department tenant mismatch");
        }
        boolean hasChildren = repository.findByTenantId(command.tenantId()).stream()
                .anyMatch(candidate -> Objects.equals(command.departmentId(), candidate.getParentId()));
        if (hasChildren) {
            throw new DomainRuleViolationException("department still has child departments");
        }
        repository.deleteById(command.departmentId());
        repository.rebuildClosure(command.tenantId());
        return department;
    }
}
