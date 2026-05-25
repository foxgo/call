package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallAnalysisResultEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CallAnalysisResultMapper extends BaseMapper<CallAnalysisResultEntity> {

    @Insert({
            "INSERT INTO call_analysis_result (tenant_id, call_id, status, tags, risk_flag, quality_score, ai_version,",
            "error_message, completed_at, created_at, updated_at)",
            "VALUES (#{result.tenantId}, #{result.callId}, #{result.status}, #{result.tags}, #{result.riskFlag},",
            "#{result.qualityScore}, #{result.aiVersion}, #{result.errorMessage}, #{result.completedAt},",
            "#{result.createdAt}, #{result.updatedAt})",
            "ON DUPLICATE KEY UPDATE",
            "status = VALUES(status),",
            "tags = VALUES(tags),",
            "risk_flag = VALUES(risk_flag),",
            "quality_score = VALUES(quality_score),",
            "ai_version = VALUES(ai_version),",
            "error_message = VALUES(error_message),",
            "completed_at = VALUES(completed_at),",
            "updated_at = VALUES(updated_at)"
    })
    int upsert(@Param("result") CallAnalysisResultEntity result);

    @Select({
            "SELECT id, tenant_id, call_id, status, tags, risk_flag, quality_score, ai_version,",
            "error_message, completed_at, created_at, updated_at",
            "FROM call_analysis_result",
            "WHERE tenant_id = #{tenantId} AND call_id = #{callId}"
    })
    CallAnalysisResultEntity selectByTenantIdAndCallId(@Param("tenantId") long tenantId, @Param("callId") long callId);
}
