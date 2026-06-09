package com.callcenter.ingestion.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CallRoundMapper extends BaseMapper<CallRoundEntity> {

    @Insert({
            "<script>",
            "INSERT IGNORE INTO call_round ",
            "(round_id, call_id, tenant_id, round_index, speaker, content, intent, start_time, created_at) VALUES ",
            "<foreach collection='rounds' item='round' separator=','>",
            "(#{round.roundId}, #{round.callId}, #{round.tenantId}, #{round.roundIndex}, #{round.speaker},",
            "#{round.content}, #{round.intent}, #{round.startTime}, #{round.createdAt})",
            "</foreach>",
            "</script>"
    })
    int batchInsertIgnore(@Param("rounds") List<CallRoundEntity> rounds);

    @Select("SELECT COUNT(1) FROM call_round WHERE call_id = #{callId}")
    long countByCallId(@Param("callId") long callId);

    @Select("""
            <script>
            SELECT round_id, call_id, tenant_id, round_index, speaker, content, intent, start_time, created_at
            FROM call_round
            WHERE call_id = #{callId}
            ORDER BY round_index ASC, round_id ASC
            </script>
            """)
    List<CallRoundEntity> selectByCallId(@Param("callId") long callId);
}
