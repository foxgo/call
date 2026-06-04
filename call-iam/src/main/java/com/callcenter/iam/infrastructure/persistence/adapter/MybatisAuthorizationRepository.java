package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.callcenter.iam.application.authorization.AuthorizationRepository;
import com.callcenter.iam.application.authorization.RoleDataScope;
import com.callcenter.iam.domain.authorization.Permission;
import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.infrastructure.persistence.dataobject.DepartmentClosureDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.RoleDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.RoleDataScopeDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.RolePermissionDO;
import com.callcenter.iam.infrastructure.persistence.mapper.DepartmentClosureMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.RoleDataScopeMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.RoleMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.RolePermissionMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAuthorizationRepository implements AuthorizationRepository {

    private static final List<Permission> BUILT_IN_PERMISSIONS = List.of(
            new Permission(101L, "iam:user:create", "Create User"),
            new Permission(102L, "iam:user:update", "Update User"),
            new Permission(103L, "iam:user:delete", "Delete User"),
            new Permission(201L, "iam:role:update", "Update Role"),
            new Permission(202L, "iam:role:scope", "Update Role Scope")
    );

    private static final Map<Long, Permission> PERMISSION_BY_ID = BUILT_IN_PERMISSIONS.stream()
            .collect(java.util.stream.Collectors.toMap(Permission::getId, permission -> permission));

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final DepartmentClosureMapper departmentClosureMapper;

    public MybatisAuthorizationRepository(
            RoleMapper roleMapper,
            RolePermissionMapper rolePermissionMapper,
            RoleDataScopeMapper roleDataScopeMapper,
            DepartmentClosureMapper departmentClosureMapper
    ) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.departmentClosureMapper = departmentClosureMapper;
    }

    @Override
    public boolean existsRoleCode(Long tenantId, String roleCode) {
        return roleMapper.selectCount(new LambdaQueryWrapper<RoleDO>()
                .eq(RoleDO::getTenantId, tenantId)
                .eq(RoleDO::getRoleCode, roleCode)) > 0;
    }

    @Override
    public Role saveRole(Role role) {
        RoleDO dataObject = new RoleDO();
        dataObject.setId(role.getId());
        dataObject.setTenantId(role.getTenantId());
        dataObject.setRoleCode(role.getRoleCode());
        dataObject.setRoleName(role.getRoleName());
        dataObject.setRoleType(role.getRoleType());
        dataObject.setBuiltIn(Boolean.FALSE);
        dataObject.setStatus("ACTIVE");
        if (roleMapper.selectById(role.getId()) == null) {
            roleMapper.insert(dataObject);
        } else {
            roleMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public List<Role> findRolesByTenantId(Long tenantId) {
        return roleMapper.selectList(new LambdaQueryWrapper<RoleDO>()
                        .eq(RoleDO::getTenantId, tenantId)
                        .orderByAsc(RoleDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Role> findRoleById(Long roleId) {
        return Optional.ofNullable(roleMapper.selectById(roleId)).map(this::toDomain);
    }

    @Override
    public void deleteRoleById(Long roleId) {
        roleMapper.deleteById(roleId);
        rolePermissionMapper.delete(new LambdaUpdateWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        roleDataScopeMapper.delete(new LambdaUpdateWrapper<RoleDataScopeDO>().eq(RoleDataScopeDO::getRoleId, roleId));
    }

    @Override
    public List<Permission> findAllPermissions() {
        return BUILT_IN_PERMISSIONS;
    }

    @Override
    public void replaceRolePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaUpdateWrapper<RolePermissionDO>().eq(RolePermissionDO::getRoleId, roleId));
        for (Long permissionId : permissionIds.stream().distinct().toList()) {
            RolePermissionDO dataObject = new RolePermissionDO();
            dataObject.setTenantId(tenantId);
            dataObject.setRoleId(roleId);
            dataObject.setPermissionId(permissionId);
            rolePermissionMapper.insert(dataObject);
        }
    }

    @Override
    public List<Long> findPermissionIdsByRoleId(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermissionDO>()
                        .eq(RolePermissionDO::getRoleId, roleId))
                .stream()
                .map(RolePermissionDO::getPermissionId)
                .sorted()
                .toList();
    }

    @Override
    public boolean allPermissionIdsExist(List<Long> permissionIds) {
        return permissionIds.stream().allMatch(PERMISSION_BY_ID::containsKey);
    }

    @Override
    public void replaceRoleDataScope(Long tenantId, Long roleId, String scopeType, Long departmentId) {
        roleDataScopeMapper.delete(new LambdaUpdateWrapper<RoleDataScopeDO>().eq(RoleDataScopeDO::getRoleId, roleId));
        RoleDataScopeDO dataObject = new RoleDataScopeDO();
        dataObject.setId(nextRoleDataScopeId());
        dataObject.setTenantId(tenantId);
        dataObject.setRoleId(roleId);
        dataObject.setScopeType(scopeType);
        dataObject.setDepartmentId(departmentId);
        roleDataScopeMapper.insert(dataObject);
    }

    @Override
    public List<RoleDataScope> findRoleDataScopes(Long tenantId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return roleDataScopeMapper.selectList(new LambdaQueryWrapper<RoleDataScopeDO>()
                        .eq(RoleDataScopeDO::getTenantId, tenantId)
                        .in(RoleDataScopeDO::getRoleId, roleIds))
                .stream()
                .map(dataObject -> new RoleDataScope(
                        dataObject.getRoleId(),
                        dataObject.getTenantId(),
                        dataObject.getScopeType(),
                        dataObject.getDepartmentId()
                ))
                .toList();
    }

    @Override
    public List<Long> findDescendantDepartmentIds(Long tenantId, Long departmentId) {
        return departmentClosureMapper.selectList(new LambdaQueryWrapper<DepartmentClosureDO>()
                        .eq(DepartmentClosureDO::getTenantId, tenantId)
                        .eq(DepartmentClosureDO::getAncestorId, departmentId))
                .stream()
                .map(DepartmentClosureDO::getDescendantId)
                .distinct()
                .toList();
    }

    private long nextRoleDataScopeId() {
        return roleDataScopeMapper.selectList(null).stream()
                .mapToLong(scope -> scope.getId() == null ? 0L : scope.getId())
                .max()
                .orElse(0L) + 1;
    }

    private Role toDomain(RoleDO dataObject) {
        return new Role(
                dataObject.getId(),
                dataObject.getTenantId(),
                dataObject.getRoleCode(),
                dataObject.getRoleName(),
                dataObject.getRoleType()
        );
    }
}
