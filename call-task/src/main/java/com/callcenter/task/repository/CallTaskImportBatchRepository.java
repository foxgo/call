package com.callcenter.task.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.callcenter.common.context.DbRouteContextHolder;
import com.callcenter.common.entity.CallTaskImportBatchEntity;
import com.callcenter.common.mapper.CallTaskImportBatchMapper;
import com.callcenter.common.route.ShardingRouter;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Repository;

@Repository
public class CallTaskImportBatchRepository {

    private final CallTaskImportBatchMapper importBatchMapper;
    private final ShardingRouter shardingRouter;

    public CallTaskImportBatchRepository(CallTaskImportBatchMapper importBatchMapper, ShardingRouter shardingRouter) {
        this.importBatchMapper = importBatchMapper;
        this.shardingRouter = shardingRouter;
    }

    public void insert(CallTaskImportBatchEntity entity) {
        withTenantRoute(entity.getTenantId(), () -> importBatchMapper.insert(entity));
    }

    public void updateById(CallTaskImportBatchEntity entity) {
        withTenantRoute(entity.getTenantId(), () -> importBatchMapper.updateById(entity));
    }

    public CallTaskImportBatchEntity findRequired(long tenantId, long taskId, long importBatchId) {
        CallTaskImportBatchEntity entity = withTenantRoute(tenantId, () -> {
            QueryWrapper<CallTaskImportBatchEntity> query = new QueryWrapper<>();
            query.eq("id", importBatchId).eq("tenant_id", tenantId).eq("task_id", taskId);
            return importBatchMapper.selectOne(query);
        });
        if (entity == null) {
            throw new NoSuchElementException("Import batch not found: " + importBatchId);
        }
        return entity;
    }

    private void withTenantRoute(long tenantId, Runnable action) {
        withTenantRoute(tenantId, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withTenantRoute(long tenantId, TaskSupplier<T> supplier) {
        DbRouteContextHolder.set(shardingRouter.dbIndex(tenantId));
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
