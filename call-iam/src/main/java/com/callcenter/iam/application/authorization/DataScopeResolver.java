package com.callcenter.iam.application.authorization;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DataScopeResolver {

    private final AuthorizationRepository authorizationRepository;

    public DataScopeResolver(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    public ResolvedDataScope resolve(Long tenantId, Long operatorUserId, List<Long> roleIds) {
        List<RoleDataScope> scopes = authorizationRepository.findRoleDataScopes(tenantId, roleIds == null ? List.of() : roleIds);
        if (scopes.stream().anyMatch(scope -> "ALL".equals(scope.scopeType()))) {
            return new ResolvedDataScope(true, List.of(), false, null);
        }
        boolean selfOnly = false;
        Set<Long> departmentIds = new LinkedHashSet<>();
        for (RoleDataScope scope : scopes) {
            switch (scope.scopeType()) {
                case "DEPARTMENT", "CUSTOM" -> {
                    if (scope.departmentId() != null) {
                        departmentIds.add(scope.departmentId());
                    }
                }
                case "DEPARTMENT_AND_CHILD" -> {
                    if (scope.departmentId() != null) {
                        departmentIds.addAll(authorizationRepository.findDescendantDepartmentIds(tenantId, scope.departmentId()));
                    }
                }
                case "SELF" -> selfOnly = true;
                default -> {
                }
            }
        }
        return new ResolvedDataScope(false, List.copyOf(departmentIds), selfOnly, selfOnly ? operatorUserId : null);
    }
}
