package com.callcenter.iam.domain.authorization;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Objects;

public class Role {

    private final Long id;
    private final Long tenantId;
    private final String roleCode;
    private final String roleName;
    private final String roleType;

    public Role(Long id, Long tenantId, String roleCode, String roleName, String roleType) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = tenantId;
        this.roleCode = requireText(roleCode, "roleCode must not be blank");
        this.roleName = requireText(roleName, "roleName must not be blank");
        this.roleType = requireText(roleType, "roleType must not be blank");
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getRoleType() {
        return roleType;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(message);
        }
        return value;
    }
}
