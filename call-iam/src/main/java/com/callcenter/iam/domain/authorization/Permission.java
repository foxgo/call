package com.callcenter.iam.domain.authorization;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Objects;

public class Permission {

    private final Long id;
    private final String permissionCode;
    private final String permissionName;

    public Permission(Long id, String permissionCode, String permissionName) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.permissionCode = requireText(permissionCode, "permissionCode must not be blank");
        this.permissionName = requireText(permissionName, "permissionName must not be blank");
    }

    public Long getId() {
        return id;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(message);
        }
        return value;
    }
}
