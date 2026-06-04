package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.callcenter.iam.application.user.UserAssignmentRepository;
import com.callcenter.iam.infrastructure.persistence.dataobject.RoleDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.UserDepartmentDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.UserRoleDO;
import com.callcenter.iam.infrastructure.persistence.mapper.RoleMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.UserDepartmentMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.UserRoleMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisUserAssignmentRepository implements UserAssignmentRepository {

    private final UserRoleMapper userRoleMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final RoleMapper roleMapper;

    public MybatisUserAssignmentRepository(
            UserRoleMapper userRoleMapper,
            UserDepartmentMapper userDepartmentMapper,
            RoleMapper roleMapper
    ) {
        this.userRoleMapper = userRoleMapper;
        this.userDepartmentMapper = userDepartmentMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<Long> findRoleIds(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleDO>()
                        .eq(UserRoleDO::getUserId, userId))
                .stream()
                .map(UserRoleDO::getRoleId)
                .sorted()
                .toList();
    }

    @Override
    public List<Long> findDepartmentIds(Long userId) {
        return userDepartmentMapper.selectList(new LambdaQueryWrapper<UserDepartmentDO>()
                        .eq(UserDepartmentDO::getUserId, userId))
                .stream()
                .map(UserDepartmentDO::getDepartmentId)
                .sorted()
                .toList();
    }

    @Override
    public void replaceRoleIds(Long tenantId, Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaUpdateWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        for (Long roleId : roleIds.stream().distinct().toList()) {
            UserRoleDO dataObject = new UserRoleDO();
            dataObject.setTenantId(tenantId);
            dataObject.setUserId(userId);
            dataObject.setRoleId(roleId);
            userRoleMapper.insert(dataObject);
        }
    }

    @Override
    public void replaceDepartmentIds(Long tenantId, Long userId, List<Long> departmentIds) {
        userDepartmentMapper.delete(new LambdaUpdateWrapper<UserDepartmentDO>().eq(UserDepartmentDO::getUserId, userId));
        boolean primary = true;
        for (Long departmentId : departmentIds.stream().distinct().toList()) {
            UserDepartmentDO dataObject = new UserDepartmentDO();
            dataObject.setTenantId(tenantId);
            dataObject.setUserId(userId);
            dataObject.setDepartmentId(departmentId);
            dataObject.setIsPrimary(primary);
            userDepartmentMapper.insert(dataObject);
            primary = false;
        }
    }

    @Override
    public boolean allRoleIdsBelongToTenant(Long tenantId, List<Long> roleIds) {
        List<Long> distinctRoleIds = roleIds.stream().distinct().toList();
        if (distinctRoleIds.isEmpty()) {
            return true;
        }
        List<RoleDO> roles = roleMapper.selectBatchIds(distinctRoleIds);
        return roles.size() == distinctRoleIds.size()
                && roles.stream().allMatch(role -> tenantId.equals(role.getTenantId()));
    }

    @Override
    public void deleteByUserId(Long userId) {
        userRoleMapper.delete(new LambdaUpdateWrapper<UserRoleDO>().eq(UserRoleDO::getUserId, userId));
        userDepartmentMapper.delete(new LambdaUpdateWrapper<UserDepartmentDO>().eq(UserDepartmentDO::getUserId, userId));
    }
}
