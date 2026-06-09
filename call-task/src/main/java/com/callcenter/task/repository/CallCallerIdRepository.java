package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.task.entity.CallCallerIdEntity;
import com.callcenter.task.mapper.CallCallerIdMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class CallCallerIdRepository {

    private final CallCallerIdMapper callCallerIdMapper;

    public CallCallerIdRepository(CallCallerIdMapper callCallerIdMapper) {
        this.callCallerIdMapper = callCallerIdMapper;
    }

    public List<CallCallerIdEntity> listActiveByTenant(Long tenantId) {
        QueryWrapper<CallCallerIdEntity> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId)
                .eq("status", "ACTIVE")
                .orderByAsc("id");
        return callCallerIdMapper.selectList(query);
    }

    public List<CallCallerIdEntity> listActiveByTenantAndPoolType(Long tenantId, String poolType) {
        QueryWrapper<CallCallerIdEntity> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId)
                .eq("status", "ACTIVE")
                .eq("pool_type", poolType)
                .orderByAsc("id");
        return callCallerIdMapper.selectList(query);
    }

    public List<CallCallerIdEntity> listByIds(Long tenantId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        QueryWrapper<CallCallerIdEntity> query = new QueryWrapper<>();
        query.eq("tenant_id", tenantId)
                .in("id", ids)
                .orderByAsc("id");
        return callCallerIdMapper.selectList(query);
    }
}
