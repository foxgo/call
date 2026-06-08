package com.callcenter.iam.interfaces.rest.user.response;

import java.util.List;

public record CurrentUserProfileResponse(
        Long userId,
        String displayName,
        Long tenantId,
        List<Long> roleIds,
        List<Long> departmentIds
) {
}
