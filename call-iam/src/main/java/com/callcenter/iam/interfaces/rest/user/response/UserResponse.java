package com.callcenter.iam.interfaces.rest.user.response;

import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String mobile,
        String email,
        String nickname,
        String status,
        List<Long> roleIds,
        List<Long> departmentIds
) {
}
