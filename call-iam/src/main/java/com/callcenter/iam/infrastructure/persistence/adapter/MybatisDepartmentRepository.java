package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.organization.DepartmentRepository;
import com.callcenter.iam.infrastructure.persistence.dataobject.DepartmentDO;
import com.callcenter.iam.infrastructure.persistence.mapper.DepartmentMapper;
import java.util.List;
import java.util.Optional;

public class MybatisDepartmentRepository implements DepartmentRepository {

    private final DepartmentMapper departmentMapper;

    public MybatisDepartmentRepository(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    @Override
    public Department save(Department department) {
        DepartmentDO dataObject = toDataObject(department);
        if (departmentMapper.selectById(department.getId()) == null) {
            departmentMapper.insert(dataObject);
        } else {
            departmentMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<Department> findById(Long id) {
        return Optional.ofNullable(departmentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<Department> findByTenantId(Long tenantId) {
        LambdaQueryWrapper<DepartmentDO> query = new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenantId)
                .orderByAsc(DepartmentDO::getSort, DepartmentDO::getId);
        return departmentMapper.selectList(query).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByTenantIdAndParentIdAndName(Long tenantId, Long parentId, String name) {
        LambdaQueryWrapper<DepartmentDO> query = new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenantId)
                .eq(DepartmentDO::getParentId, parentId)
                .eq(DepartmentDO::getName, name);
        return departmentMapper.selectCount(query) > 0;
    }

    private DepartmentDO toDataObject(Department department) {
        DepartmentDO dataObject = new DepartmentDO();
        dataObject.setId(department.getId());
        dataObject.setTenantId(department.getTenantId());
        dataObject.setParentId(department.getParentId());
        dataObject.setName(department.getName());
        dataObject.setStatus(department.getStatus());
        dataObject.setSort(department.getSort());
        return dataObject;
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
