package com.callcenter.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.task.entity.CallTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallTaskMapper extends BaseMapper<CallTaskEntity> {
}
