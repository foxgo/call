package com.callcenter.iam.application.organization;

import com.callcenter.iam.domain.organization.Department;
import java.util.List;
import java.util.Optional;

public interface DepartmentTreeRepository {

    Department save(Department department);

    Optional<Department> findById(Long id);

    List<Department> findAll();

    List<Department> findByTenantId(Long tenantId);

    boolean existsByTenantIdAndParentIdAndName(Long tenantId, Long parentId, String name);

    boolean isDescendant(Long tenantId, Long ancestorId, Long descendantId);

    void deleteById(Long id);

    void rebuildClosure(Long tenantId);
}
