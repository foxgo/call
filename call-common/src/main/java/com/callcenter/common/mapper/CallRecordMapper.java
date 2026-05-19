package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallRecordEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecordEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO call_record ",
            "(call_id, tenant_id, task_id, phone, line_number, call_status, duration, start_time, end_time, created_at) VALUES ",
            "<foreach collection='records' item='record' separator=','>",
            "(#{record.callId}, #{record.tenantId}, #{record.taskId}, #{record.phone}, #{record.lineNumber},",
            "#{record.callStatus}, #{record.duration}, #{record.startTime}, #{record.endTime}, #{record.createdAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsertIgnore(@Param("records") List<CallRecordEntity> records);
}

