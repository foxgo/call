package com.callcenter.iam.infrastructure.persistence.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.domain.user.UserStatus;
import com.callcenter.iam.domain.user.UserType;
import com.callcenter.iam.infrastructure.persistence.dataobject.UserDepartmentDO;
import com.callcenter.iam.infrastructure.persistence.dataobject.UserDO;
import com.callcenter.iam.infrastructure.persistence.mapper.UserDepartmentMapper;
import com.callcenter.iam.infrastructure.persistence.mapper.UserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisUserRepository implements UserRepository {

    private final UserMapper userMapper;
    private final UserDepartmentMapper userDepartmentMapper;

    public MybatisUserRepository(UserMapper userMapper, UserDepartmentMapper userDepartmentMapper) {
        this.userMapper = userMapper;
        this.userDepartmentMapper = userDepartmentMapper;
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
    public List<User> findAll() {
        return userMapper.selectList(null).stream()
                .map(this::toDomain)
                .sorted(java.util.Comparator.comparing(User::getId))
                .toList();
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<User> findByTenantId(Long tenantId) {
        return userMapper.selectList(new LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getTenantId, tenantId)
                        .orderByAsc(UserDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<User> findByTenantIdAndDepartmentId(Long tenantId, Long departmentId) {
        List<Long> userIds = userDepartmentMapper.selectList(new LambdaQueryWrapper<UserDepartmentDO>()
                        .eq(UserDepartmentDO::getTenantId, tenantId)
                        .eq(UserDepartmentDO::getDepartmentId, departmentId))
                .stream()
                .map(UserDepartmentDO::getUserId)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .map(this::toDomain)
                .sorted(java.util.Comparator.comparing(User::getId))
                .toList();
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

    @Override
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }

    private <T> Optional<User> selectOne(Long tenantId, com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserDO, T> column, T value) {
        LambdaQueryWrapper<UserDO> query = new LambdaQueryWrapper<UserDO>()
                .eq(column, value)
                .last("LIMIT 1");
        if (tenantId == null) {
            query.isNull(UserDO::getTenantId);
        } else {
            query.eq(UserDO::getTenantId, tenantId);
        }
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
