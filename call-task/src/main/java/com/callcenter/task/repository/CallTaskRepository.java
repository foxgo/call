package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.common.context.DbRouteContextHolder;
import com.callcenter.common.entity.CallTaskEntity;
import com.callcenter.common.mapper.CallTaskMapper;
import com.callcenter.common.route.ShardingRouter;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Repository;

@Repository
public class CallTaskRepository {

    private final CallTaskMapper callTaskMapper;
    private final ShardingRouter shardingRouter;

    public CallTaskRepository(
            CallTaskMapper callTaskMapper,
            ShardingRouter shardingRouter
    ) {
        this.callTaskMapper = callTaskMapper;
        this.shardingRouter = shardingRouter;
    }

    public void insert(CallTaskEntity entity) {
        withTenantRoute(entity.getTenantId(), () -> callTaskMapper.insert(entity));
    }

    public void updateById(CallTaskEntity entity) {
        withTenantRoute(entity.getTenantId(), () -> callTaskMapper.updateById(entity));
    }

    public CallTaskEntity findRequired(long tenantId, long taskId) {
        CallTaskEntity entity = withTenantRoute(tenantId, () -> {
            QueryWrapper<CallTaskEntity> query = new QueryWrapper<>();
            query.eq("id", taskId).eq("tenant_id", tenantId);
            return callTaskMapper.selectOne(query);
        });
        if (entity == null) {
            throw new NoSuchElementException("Task not found: " + taskId);
        }
        return entity;
    }

    public List<CallTaskEntity> listByTenant(long tenantId) {
        return withTenantRoute(tenantId, () -> {
            QueryWrapper<CallTaskEntity> query = new QueryWrapper<>();
            query.eq("tenant_id", tenantId).orderByDesc("updated_at");
            return callTaskMapper.selectList(query);
        });
    }

    private void withTenantRoute(long tenantId, Runnable action) {
        withDbRoute(shardingRouter.dbIndex(tenantId), () -> {
            action.run();
            return null;
        });
    }

    private <T> T withTenantRoute(long tenantId, TaskSupplier<T> supplier) {
        return withDbRoute(shardingRouter.dbIndex(tenantId), supplier);
    }

    private <T> T withDbRoute(int dbIndex, TaskSupplier<T> supplier) {
        DbRouteContextHolder.set(dbIndex);
        try {
            return supplier.get();
        } finally {
            DbRouteContextHolder.clear();
        }
    }

    @FunctionalInterface
    private interface TaskSupplier<T> {
        T get();
    }
}
