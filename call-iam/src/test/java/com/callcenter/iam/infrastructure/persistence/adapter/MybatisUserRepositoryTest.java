package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.callcenter.iam.infrastructure.persistence.dataobject.UserDO;
import com.callcenter.iam.infrastructure.persistence.mapper.UserDepartmentMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisUserRepositoryTest {

    @Test
    void shouldQueryPlatformUserWithTenantIdIsNull() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserDO.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserDepartmentMapper userDepartmentMapper = mock(UserDepartmentMapper.class);
        when(userMapper.selectOne(any())).thenReturn(null);
        MybatisUserRepository repository = new MybatisUserRepository(userMapper, userDepartmentMapper);

        repository.findByTenantIdAndUsername(null, "platform-admin");

        ArgumentCaptor<LambdaQueryWrapper<UserDO>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).contains("tenant_id IS NULL");
    }
}
