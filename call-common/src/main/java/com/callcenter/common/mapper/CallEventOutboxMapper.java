package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallEventOutboxEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallEventOutboxMapper extends BaseMapper<CallEventOutboxEntity> {

    @Insert({
            "<script>",
            "INSERT INTO call_event_outbox ",
            "(event_id, event_type, aggregate_type, aggregate_id, tenant_id, partition_key, schema_version, payload, status, attempt_count, next_attempt_at, last_error, created_at, updated_at) VALUES ",
            "<foreach collection='events' item='event' separator=','>",
            "(#{event.eventId}, #{event.eventType}, #{event.aggregateType}, #{event.aggregateId}, #{event.tenantId},",
            "#{event.partitionKey}, #{event.schemaVersion}, #{event.payload}, #{event.status}, #{event.attemptCount},",
            "#{event.nextAttemptAt}, #{event.lastError}, #{event.createdAt}, #{event.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("events") List<CallEventOutboxEntity> events);
}
