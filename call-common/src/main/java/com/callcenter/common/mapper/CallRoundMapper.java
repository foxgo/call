package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallRoundEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
