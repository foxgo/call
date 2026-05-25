package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallDeadLetterTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallDeadLetterTaskMapper extends BaseMapper<CallDeadLetterTaskEntity> {

    @Insert({
            "INSERT IGNORE INTO call_dead_letter_task (",
            "task_key, message_type, source_topic, source_partition, source_offset, dlq_topic, dlq_queue_offset, ",
            "origin_message_id, message_key, idempotency_key, payload_type, payload, status, dlq_attempt, ",
            "dlq_max_attempts, first_failure_at, last_failure_at, error_class, error_message, created_at, updated_at",
            ") VALUES (",
            "#{task.taskKey}, #{task.messageType}, #{task.sourceTopic}, #{task.sourcePartition}, #{task.sourceOffset}, ",
            "#{task.dlqTopic}, #{task.dlqQueueOffset}, #{task.originMessageId}, #{task.messageKey}, #{task.idempotencyKey}, ",
            "#{task.payloadType}, #{task.payload}, #{task.status}, #{task.dlqAttempt}, #{task.dlqMaxAttempts}, ",
            "#{task.firstFailureAt}, #{task.lastFailureAt}, #{task.errorClass}, #{task.errorMessage}, ",
            "#{task.createdAt}, #{task.updatedAt}",
            ")"
    })
    int insertIgnore(@Param("task") CallDeadLetterTaskEntity task);
}
