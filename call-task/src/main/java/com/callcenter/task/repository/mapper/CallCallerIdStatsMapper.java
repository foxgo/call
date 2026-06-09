package com.callcenter.task.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.task.repository.entity.CallCallerIdStatsEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallCallerIdStatsMapper extends BaseMapper<CallCallerIdStatsEntity> {

    @Insert({
            "INSERT INTO call_caller_id_stats ",
            "(id, tenant_id, caller_id_id, attempt_stage, time_bucket, attempt_count, ring_count, answer_count, ",
            "success_count, total_talk_seconds, failure_code_summary, health_score, updated_at) ",
            "VALUES (#{stats.id}, #{stats.tenantId}, #{stats.callerIdId}, #{stats.attemptStage}, #{stats.timeBucket}, ",
            "#{stats.attemptCount}, #{stats.ringCount}, #{stats.answerCount}, #{stats.successCount}, ",
            "#{stats.totalTalkSeconds}, #{stats.failureCodeSummary}, #{stats.healthScore}, #{stats.updatedAt}) ",
            "ON DUPLICATE KEY UPDATE ",
            "attempt_count = VALUES(attempt_count), ",
            "ring_count = VALUES(ring_count), ",
            "answer_count = VALUES(answer_count), ",
            "success_count = VALUES(success_count), ",
            "total_talk_seconds = VALUES(total_talk_seconds), ",
            "failure_code_summary = VALUES(failure_code_summary), ",
            "health_score = VALUES(health_score), ",
            "updated_at = VALUES(updated_at)"
    })
    int upsert(@Param("stats") CallCallerIdStatsEntity stats);
}
