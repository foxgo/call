package com.callcenter.iam.application.organization;

import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpdateDepartmentUseCase {

    private final DepartmentTreeRepository repository;

    public UpdateDepartmentUseCase(DepartmentTreeRepository repository) {
        this.repository = repository;
    }

    public Department execute(UpdateDepartmentCommand command) {
        Department department = repository.findById(command.departmentId())
                .orElseThrow(() -> new DomainRuleViolationException("department not found"));
        if (!command.tenantId().equals(department.getTenantId())) {
            throw new DomainRuleViolationException("department tenant mismatch");
        }
        if (!Objects.equals(command.name(), department.getName())
                && repository.existsByTenantIdAndParentIdAndName(command.tenantId(), department.getParentId(), command.name())) {
            throw new DomainRuleViolationException("department name already exists under parent");
        }
        department.updateBasics(command.name(), command.status(), command.sort());
        return repository.save(department);
    }
}
