package com.callcenter.iam.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.callcenter.iam.infrastructure.persistence.dataobject.RolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {
}
