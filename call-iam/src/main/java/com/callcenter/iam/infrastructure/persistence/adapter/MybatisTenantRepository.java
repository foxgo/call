package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.tenant.TenantStatus;
import com.callcenter.iam.infrastructure.persistence.dataobject.TenantDO;
import com.callcenter.iam.infrastructure.persistence.mapper.TenantMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisTenantRepository implements TenantRepository {

    private final TenantMapper tenantMapper;

    public MybatisTenantRepository(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantDO dataObject = toDataObject(tenant);
        if (tenantMapper.selectById(tenant.getId()) == null) {
            tenantMapper.insert(dataObject);
        } else {
            tenantMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<Tenant> findById(Long id) {
        return Optional.ofNullable(tenantMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Tenant> findByTenantCode(String tenantCode) {
        LambdaQueryWrapper<TenantDO> query = new LambdaQueryWrapper<TenantDO>()
                .eq(TenantDO::getTenantCode, tenantCode)
                .last("LIMIT 1");
        return Optional.ofNullable(tenantMapper.selectOne(query)).map(this::toDomain);
    }

    @Override
    public List<Tenant> findAll() {
        return tenantMapper.selectList(new LambdaQueryWrapper<TenantDO>().orderByAsc(TenantDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private TenantDO toDataObject(Tenant tenant) {
        TenantDO dataObject = new TenantDO();
        dataObject.setId(tenant.getId());
        dataObject.setTenantCode(tenant.getTenantCode());
        dataObject.setTenantName(tenant.getTenantName());
        dataObject.setStatus(tenant.getStatus().name());
        dataObject.setExpireTime(tenant.getExpireTime());
        return dataObject;
    }

    private Tenant toDomain(TenantDO dataObject) {
        TenantStatus status = TenantStatus.valueOf(dataObject.getStatus());
        return switch (status) {
            case ACTIVE -> Tenant.active(dataObject.getId(), dataObject.getTenantCode(), dataObject.getTenantName(), dataObject.getExpireTime());
            case SUSPENDED -> Tenant.suspended(dataObject.getId(), dataObject.getTenantCode(), dataObject.getTenantName(), dataObject.getExpireTime());
            case DELETED -> Tenant.deleted(dataObject.getId(), dataObject.getTenantCode(), dataObject.getTenantName());
            case EXPIRED -> {
                Tenant tenant = Tenant.active(dataObject.getId(), dataObject.getTenantCode(), dataObject.getTenantName(), dataObject.getExpireTime());
                tenant.expire();
                yield tenant;
            }
        };
    }
}
