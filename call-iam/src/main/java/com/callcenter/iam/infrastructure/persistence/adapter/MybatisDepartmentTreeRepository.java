package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.callcenter.iam.application.organization.DepartmentTreeRepository;
import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.infrastructure.persistence.dataobject.DepartmentClosureDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.DepartmentDO;
import com.callcenter.iam.infrastructure.persistence.mapper.DepartmentClosureMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.DepartmentMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisDepartmentTreeRepository implements DepartmentTreeRepository {

    private final DepartmentMapper departmentMapper;
    private final DepartmentClosureMapper departmentClosureMapper;

    public MybatisDepartmentTreeRepository(DepartmentMapper departmentMapper, DepartmentClosureMapper departmentClosureMapper) {
        this.departmentMapper = departmentMapper;
        this.departmentClosureMapper = departmentClosureMapper;
    }

    @Override
    public Department save(Department department) {
        DepartmentDO dataObject = new DepartmentDO();
        dataObject.setId(department.getId());
        dataObject.setTenantId(department.getTenantId());
        dataObject.setParentId(department.getParentId());
        dataObject.setName(department.getName());
        dataObject.setStatus(department.getStatus());
        dataObject.setSort(department.getSort());
        if (departmentMapper.selectById(department.getId()) == null) {
            departmentMapper.insert(dataObject);
        } else {
            departmentMapper.updateById(dataObject);
        }
        return department;
    }

    @Override
    public Optional<Department> findById(Long id) {
        return Optional.ofNullable(departmentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Department> findAll() {
        return departmentMapper.selectList(null).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Department> findByTenantId(Long tenantId) {
        return departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                        .eq(DepartmentDO::getTenantId, tenantId)
                        .orderByAsc(DepartmentDO::getSort, DepartmentDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByTenantIdAndParentIdAndName(Long tenantId, Long parentId, String name) {
        return departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenantId)
                .eq(DepartmentDO::getParentId, parentId)
                .eq(DepartmentDO::getName, name)) > 0;
    }

    @Override
    public boolean isDescendant(Long tenantId, Long ancestorId, Long descendantId) {
        return departmentClosureMapper.selectCount(new LambdaQueryWrapper<DepartmentClosureDO>()
                .eq(DepartmentClosureDO::getTenantId, tenantId)
                .eq(DepartmentClosureDO::getAncestorId, ancestorId)
                .eq(DepartmentClosureDO::getDescendantId, descendantId)
                .gt(DepartmentClosureDO::getDepth, 0)) > 0;
    }

    @Override
    public void deleteById(Long id) {
        departmentMapper.deleteById(id);
    }

    @Override
    public void rebuildClosure(Long tenantId) {
        List<Department> departments = findByTenantId(tenantId);
        Map<Long, Department> departmentById = departments.stream()
                .collect(java.util.stream.Collectors.toMap(Department::getId, department -> department));
        departmentClosureMapper.delete(new LambdaUpdateWrapper<DepartmentClosureDO>()
                .eq(DepartmentClosureDO::getTenantId, tenantId));
        for (Department department : departments) {
            insertClosureRow(tenantId, department.getId(), department.getId(), 0);
            Department current = department;
            int depth = 1;
            while (current.getParentId() != null) {
                Department parent = departmentById.get(current.getParentId());
                if (parent == null) {
                    break;
                }
                insertClosureRow(tenantId, parent.getId(), department.getId(), depth++);
                current = parent;
            }
        }
    }

    private void insertClosureRow(Long tenantId, Long ancestorId, Long descendantId, int depth) {
        DepartmentClosureDO closureDO = new DepartmentClosureDO();
        closureDO.setTenantId(tenantId);
        closureDO.setAncestorId(ancestorId);
        closureDO.setDescendantId(descendantId);
        closureDO.setDepth(depth);
        departmentClosureMapper.insert(closureDO);
    }

    private Department toDomain(DepartmentDO dataObject) {
        return new Department(
                dataObject.getId(),
                dataObject.getTenantId(),
                dataObject.getParentId(),
                dataObject.getName(),
                dataObject.getStatus(),
                dataObject.getSort() == null ? 0 : dataObject.getSort()
        );
    }
}
