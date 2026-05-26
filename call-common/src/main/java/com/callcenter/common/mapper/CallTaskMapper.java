package com.callcenter.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.common.entity.CallTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CallTaskMapper extends BaseMapper<CallTaskEntity> {
}
