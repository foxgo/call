package com.callcenter.task.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.task.repository.entity.CallTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallTaskMapper extends BaseMapper<CallTaskEntity> {
}
