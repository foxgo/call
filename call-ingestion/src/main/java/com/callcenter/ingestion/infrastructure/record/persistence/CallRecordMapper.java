package com.callcenter.ingestion.infrastructure.record.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecordEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO call_record ",
            "(call_id, tenant_id, task_id, phone, line_number, call_status, duration, round_total,",
            " recording_url, error_code, error_description, hangup_by, connected, ring_duration,",
            " ring_start_time, hangup_time, start_time, end_time, created_at) VALUES ",
            "<foreach collection='records' item='record' separator=','>",
            "(#{record.callId}, #{record.tenantId}, #{record.taskId}, #{record.phone}, #{record.lineNumber},",
            "#{record.callStatus}, #{record.duration}, #{record.roundTotal}, #{record.recordingUrl},",
            "#{record.errorCode}, #{record.errorDescription}, #{record.hangupBy}, #{record.connected},",
            "#{record.ringDuration}, #{record.ringStartTime}, #{record.hangupTime},",
            "#{record.startTime}, #{record.endTime}, #{record.createdAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsertIgnore(@Param("records") List<CallRecordEntity> records);
}
