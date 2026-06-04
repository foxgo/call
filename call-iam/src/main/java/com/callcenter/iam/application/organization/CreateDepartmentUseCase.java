package com.callcenter.iam.application.organization;

import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import org.springframework.stereotype.Service;

@Service
public class CreateDepartmentUseCase {

    private final DepartmentTreeRepository repository;

    public CreateDepartmentUseCase(DepartmentTreeRepository repository) {
        this.repository = repository;
    }

    public Department execute(CreateDepartmentCommand command) {
        if (repository.existsByTenantIdAndParentIdAndName(command.tenantId(), command.parentId(), command.name())) {
            throw new DomainRuleViolationException("department name already exists under parent");
        }
        if (command.parentId() != null) {
            Department parent = repository.findById(command.parentId())
                    .orElseThrow(() -> new DomainRuleViolationException("parent department not found"));
            if (!command.tenantId().equals(parent.getTenantId())) {
                throw new DomainRuleViolationException("cross-tenant department creation is not allowed");
            }
        }
        Department department = new Department(
                command.id() == null ? nextId() : command.id(),
                command.tenantId(),
                command.parentId(),
                command.name(),
                command.status(),
                command.sort()
        );
        Department saved = repository.save(department);
        repository.rebuildClosure(command.tenantId());
        return saved;
    }

    private long nextId() {
        return repository.findAll().stream()
                .mapToLong(Department::getId)
                .max()
                .orElse(0L) + 1;
    }
}
