package com.callcenter.iam.application.organization;

import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import org.springframework.stereotype.Service;

@Service
public class MoveDepartmentUseCase {

    private final DepartmentTreeRepository repository;

    public MoveDepartmentUseCase(DepartmentTreeRepository repository) {
        this.repository = repository;
    }

    public Department execute(MoveDepartmentCommand command) {
        Department department = repository.findById(command.departmentId())
                .orElseThrow(() -> new DomainRuleViolationException("department not found"));
        if (!command.tenantId().equals(department.getTenantId())) {
            throw new DomainRuleViolationException("department tenant mismatch");
        }
        if (command.newParentId() != null) {
            if (command.departmentId().equals(command.newParentId())) {
                throw new DomainRuleViolationException("department cannot move under itself");
            }
            Department newParent = repository.findById(command.newParentId())
                    .orElseThrow(() -> new DomainRuleViolationException("new parent not found"));
            if (!command.tenantId().equals(newParent.getTenantId())) {
                throw new DomainRuleViolationException("cross-tenant move is not allowed");
            }
            if (repository.isDescendant(command.tenantId(), command.departmentId(), command.newParentId())) {
                throw new DomainRuleViolationException("department cannot move under its descendant");
            }
        }
        department.moveTo(command.newParentId());
        Department saved = repository.save(department);
        repository.rebuildClosure(command.tenantId());
        return saved;
    }
}
