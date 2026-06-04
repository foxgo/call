package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallDialUnitEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallDialUnitMapper extends BaseMapper<CallDialUnitEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO call_dial_unit ",
            "(call_id, tenant_id, task_id, import_batch_id, phone, status, retry_count, max_retry_count, score,",
            "last_call_time, next_call_time, dispatch_token, inflight_expire_at, biz_idempotency_key,",
            "failure_code, failure_reason, created_at, updated_at) VALUES ",
            "<foreach collection='units' item='unit' separator=','>",
            "(#{unit.id}, #{unit.tenantId}, #{unit.taskId}, #{unit.importBatchId}, #{unit.phone}, #{unit.status},",
            "#{unit.retryCount}, #{unit.maxRetryCount}, #{unit.score}, #{unit.lastCallTime}, #{unit.nextCallTime},",
            "#{unit.dispatchToken}, #{unit.inflightExpireAt}, #{unit.bizIdempotencyKey}, #{unit.failureCode},",
            "#{unit.failureReason}, #{unit.createdAt}, #{unit.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsertIgnore(@Param("units") List<CallDialUnitEntity> units);
}
