package com.callcenter.iam.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    List<User> findAll();

    Optional<User> findById(Long id);

    List<User> findByTenantId(Long tenantId);

    List<User> findByTenantIdAndDepartmentId(Long tenantId, Long departmentId);

    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

    Optional<User> findByTenantIdAndMobile(Long tenantId, String mobile);

    Optional<User> findByTenantIdAndEmail(Long tenantId, String email);

    void deleteById(Long id);
}
