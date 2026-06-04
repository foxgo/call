package com.callcenter.iam.domain.organization;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository {

    Department save(Department department);

    Optional<Department> findById(Long id);

    List<Department> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndParentIdAndName(Long tenantId, Long parentId, String name);
}
