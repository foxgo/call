package com.callcenter.iam.application.authorization;

import java.util.List;

public record ResolvedDataScope(
        boolean accessAll,
        List<Long> departmentIds,
        boolean selfOnly,
        Long operatorUserId
) {
}
