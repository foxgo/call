package com.callcenter.iam.application.authorization;

import com.callcenter.iam.domain.authorization.Permission;
import com.callcenter.iam.domain.authorization.Role;
import java.util.List;
import java.util.Optional;

public interface AuthorizationRepository {

    boolean existsRoleCode(Long tenantId, String roleCode);

    Role saveRole(Role role);

    List<Role> findRolesByTenantId(Long tenantId);

    Optional<Role> findRoleById(Long roleId);

    void deleteRoleById(Long roleId);

    List<Permission> findAllPermissions();

    void replaceRolePermissions(Long tenantId, Long roleId, List<Long> permissionIds);

    List<Long> findPermissionIdsByRoleId(Long roleId);

    boolean allPermissionIdsExist(List<Long> permissionIds);

    void replaceRoleDataScope(Long tenantId, Long roleId, String scopeType, Long departmentId);

    List<RoleDataScope> findRoleDataScopes(Long tenantId, List<Long> roleIds);

    List<Long> findDescendantDepartmentIds(Long tenantId, Long departmentId);
}
