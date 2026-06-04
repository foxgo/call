package com.callcenter.iam.application.user;

import java.util.List;

public interface UserAssignmentRepository {

    List<Long> findRoleIds(Long userId);

    List<Long> findDepartmentIds(Long userId);

    void replaceRoleIds(Long tenantId, Long userId, List<Long> roleIds);

    void replaceDepartmentIds(Long tenantId, Long userId, List<Long> departmentIds);

    boolean allRoleIdsBelongToTenant(Long tenantId, List<Long> roleIds);

    void deleteByUserId(Long userId);
}
