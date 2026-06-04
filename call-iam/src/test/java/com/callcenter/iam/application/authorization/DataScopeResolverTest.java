package com.callcenter.iam.application.authorization;

import com.callcenter.iam.domain.organization.Department;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeResolverTest {

    @Test
    void shouldResolveDescendantDepartmentsForDepartmentAndChildScope() {
        InMemoryAuthorizationRepository repository = new InMemoryAuthorizationRepository();
        repository.addScope(new RoleDataScope(11L, 9L, "DEPARTMENT_AND_CHILD", 20L));
        repository.addClosure(9L, 20L, 20L, 0);
        repository.addClosure(9L, 20L, 21L, 1);
        repository.addClosure(9L, 20L, 22L, 1);

        DataScopeResolver resolver = new DataScopeResolver(repository);

        ResolvedDataScope scope = resolver.resolve(9L, 1001L, List.of(11L));

        assertThat(scope.accessAll()).isFalse();
        assertThat(scope.selfOnly()).isFalse();
        assertThat(scope.departmentIds()).containsExactlyInAnyOrder(20L, 21L, 22L);
    }

    @Test
    void shouldAllowOnlySelfOwnedRowsForSelfScope() {
        InMemoryAuthorizationRepository repository = new InMemoryAuthorizationRepository();
        repository.addScope(new RoleDataScope(12L, 9L, "SELF", null));

        DataScopeResolver resolver = new DataScopeResolver(repository);

        ResolvedDataScope scope = resolver.resolve(9L, 1001L, List.of(12L));

        assertThat(scope.accessAll()).isFalse();
        assertThat(scope.selfOnly()).isTrue();
        assertThat(scope.operatorUserId()).isEqualTo(1001L);
        assertThat(scope.departmentIds()).isEmpty();
    }

    private static final class InMemoryAuthorizationRepository implements AuthorizationRepository {

        private final Map<Long, RoleDataScope> scopeByRoleId = new HashMap<>();
        private final Map<String, List<Long>> descendantsByAncestor = new HashMap<>();

        void addScope(RoleDataScope scope) {
            scopeByRoleId.put(scope.roleId(), scope);
        }

        void addClosure(Long tenantId, Long ancestorId, Long descendantId, int depth) {
            String key = tenantId + ":" + ancestorId;
            descendantsByAncestor.merge(key, List.of(descendantId), (left, right) -> {
                java.util.ArrayList<Long> merged = new java.util.ArrayList<>(left);
                merged.addAll(right);
                return merged;
            });
        }

        @Override
        public List<RoleDataScope> findRoleDataScopes(Long tenantId, List<Long> roleIds) {
            return roleIds.stream()
                    .map(scopeByRoleId::get)
                    .filter(java.util.Objects::nonNull)
                    .filter(scope -> tenantId.equals(scope.tenantId()))
                    .toList();
        }

        @Override
        public List<Long> findDescendantDepartmentIds(Long tenantId, Long departmentId) {
            return descendantsByAncestor.getOrDefault(tenantId + ":" + departmentId, List.of());
        }

        @Override
        public boolean existsRoleCode(Long tenantId, String roleCode) {
            return false;
        }

        @Override
        public com.callcenter.iam.domain.authorization.Role saveRole(com.callcenter.iam.domain.authorization.Role role) {
            return role;
        }

        @Override
        public List<com.callcenter.iam.domain.authorization.Role> findRolesByTenantId(Long tenantId) {
            return List.of();
        }

        @Override
        public Optional<com.callcenter.iam.domain.authorization.Role> findRoleById(Long roleId) {
            return Optional.empty();
        }

        @Override
        public void deleteRoleById(Long roleId) {
        }

        @Override
        public List<com.callcenter.iam.domain.authorization.Permission> findAllPermissions() {
            return List.of();
        }

        @Override
        public void replaceRolePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        }

        @Override
        public List<Long> findPermissionIdsByRoleId(Long roleId) {
            return List.of();
        }

        @Override
        public boolean allPermissionIdsExist(List<Long> permissionIds) {
            return true;
        }

        @Override
        public void replaceRoleDataScope(Long tenantId, Long roleId, String scopeType, Long departmentId) {
        }
    }
}
