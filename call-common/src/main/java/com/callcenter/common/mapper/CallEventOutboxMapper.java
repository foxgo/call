package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallEventOutboxEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CallEventOutboxMapper extends BaseMapper<CallEventOutboxEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO call_event_outbox ",
            "(event_id, event_type, aggregate_type, aggregate_id, tenant_id, partition_key, schema_version, payload, status, attempt_count, next_attempt_at, last_error, created_at, updated_at) VALUES ",
            "<foreach collection='events' item='event' separator=','>",
            "(#{event.eventId}, #{event.eventType}, #{event.aggregateType}, #{event.aggregateId}, #{event.tenantId},",
            "#{event.partitionKey}, #{event.schemaVersion}, #{event.payload}, #{event.status}, #{event.attemptCount},",
            "#{event.nextAttemptAt}, #{event.lastError}, #{event.createdAt}, #{event.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("events") List<CallEventOutboxEntity> events);

    @Select({
            "<script>",
            "SELECT id",
            "FROM call_event_outbox",
            "WHERE status = 'NEW'",
            "OR (status = 'FAILED' AND next_attempt_at &lt;= #{now}",
            "AND (attempt_count IS NULL OR attempt_count &lt; #{maxRetries}))",
            "ORDER BY created_at ASC, id ASC",
            "LIMIT #{limit}",
            "FOR UPDATE SKIP LOCKED",
            "</script>"
    })
    List<Long> selectPublishableIdsForClaim(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit,
            @Param("maxRetries") int maxRetries
    );

    @Select({
            "<script>",
            "SELECT id, event_id, event_type, aggregate_type, aggregate_id, tenant_id, partition_key,",
            "schema_version, payload, status, attempt_count, next_attempt_at, last_error, created_at, updated_at",
            "FROM call_event_outbox",
            "WHERE status = 'PROCESSING'",
            "AND id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "ORDER BY created_at ASC, id ASC",
            "</script>"
    })
    List<CallEventOutboxEntity> selectClaimedBatchByIds(@Param("ids") List<Long> ids);
}
