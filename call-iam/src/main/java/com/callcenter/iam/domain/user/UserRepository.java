package com.callcenter.iam.domain.user;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

    Optional<User> findByTenantIdAndMobile(Long tenantId, String mobile);

    Optional<User> findByTenantIdAndEmail(Long tenantId, String email);
}
