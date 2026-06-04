package com.callcenter.iam.infrastructure.persistence.interceptor;

import com.callcenter.iam.application.authorization.ResolvedDataScope;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DataScopeQueryCustomizer {

    public String customizeUserListQuery(String baseSql, ResolvedDataScope scope) {
        if (scope == null || scope.accessAll()) {
            return baseSql;
        }
        List<String> predicates = new ArrayList<>();
        if (!scope.departmentIds().isEmpty()) {
            predicates.add("id IN (SELECT user_id FROM user_department WHERE department_id IN (%s))"
                    .formatted(joinIds(scope.departmentIds())));
        }
        if (scope.selfOnly() && scope.operatorUserId() != null) {
            predicates.add("id = " + scope.operatorUserId());
        }
        if (predicates.isEmpty()) {
            return baseSql;
        }
        String combinedPredicate = "(" + String.join(" OR ", predicates) + ")";
        if (baseSql.toLowerCase().contains(" where ")) {
            return baseSql + " AND " + combinedPredicate;
        }
        return baseSql + " WHERE " + combinedPredicate;
    }

    private String joinIds(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }
}
