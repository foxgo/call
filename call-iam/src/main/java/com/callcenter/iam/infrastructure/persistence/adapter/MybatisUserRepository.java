package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.domain.user.UserStatus;
import com.callcenter.iam.domain.user.UserType;
import com.callcenter.iam.infrastructure.persistence.dataobject.UserDO;
import com.callcenter.iam.infrastructure.persistence.mapper.UserMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    public MybatisUserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User save(User user) {
        UserDO dataObject = toDataObject(user);
        if (userMapper.selectById(user.getId()) == null) {
            userMapper.insert(dataObject);
        } else {
            userMapper.updateById(dataObject);
        }
        return toDomain(dataObject);
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByTenantIdAndUsername(Long tenantId, String username) {
        return selectOne(tenantId, UserDO::getUsername, username);
    }

    @Override
    public Optional<User> findByTenantIdAndMobile(Long tenantId, String mobile) {
        return selectOne(tenantId, UserDO::getMobile, mobile);
    }

    @Override
    public Optional<User> findByTenantIdAndEmail(Long tenantId, String email) {
        return selectOne(tenantId, UserDO::getEmail, email);
    }

    private <T> Optional<User> selectOne(Long tenantId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserDO, T> column, T value) {
        LambdaQueryWrapper<UserDO> query = new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getTenantId, tenantId)
                .eq(column, value)
                .last("LIMIT 1");
        return Optional.ofNullable(userMapper.selectOne(query)).map(this::toDomain);
    }

    private UserDO toDataObject(User user) {
        UserDO dataObject = new UserDO();
        dataObject.setId(user.getId());
        dataObject.setTenantId(user.getTenantId());
        dataObject.setUserType(user.getUserType().name());
        dataObject.setUsername(user.getUsername());
        dataObject.setMobile(user.getMobile());
        dataObject.setEmail(user.getEmail());
        dataObject.setPasswordHash(user.getPasswordHash());
        dataObject.setNickname(user.getNickname());
        dataObject.setStatus(user.getStatus().name());
        dataObject.setLastLoginTime(user.getLastLoginTime());
        return dataObject;
    }

    private User toDomain(UserDO dataObject) {
        User user = User.createWithPasswordHash(
                dataObject.getId(),
                dataObject.getTenantId(),
                UserType.valueOf(dataObject.getUserType()),
                dataObject.getUsername(),
                dataObject.getMobile(),
                dataObject.getEmail(),
                dataObject.getPasswordHash(),
                dataObject.getNickname()
        );
        if (dataObject.getStatus() != null) {
            UserStatus status = UserStatus.valueOf(dataObject.getStatus());
            if (status == UserStatus.DISABLE) {
                user.disable();
            } else if (status == UserStatus.LOCK) {
                user.lock();
            }
        }
        if (dataObject.getLastLoginTime() != null) {
            user.markLoggedIn(dataObject.getLastLoginTime());
        }
        return user;
    }
}
