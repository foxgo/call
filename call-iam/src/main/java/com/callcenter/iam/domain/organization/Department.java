package com.callcenter.iam.domain.organization;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.util.Objects;

public class Department {

    private final Long id;
    private final Long tenantId;
    private Long parentId;
    private String name;
    private String status;
    private int sort;

    public Department(Long id, Long tenantId, Long parentId, String name, String status, int sort) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.parentId = parentId;
        this.name = requireText(name, "name must not be blank");
        this.status = requireText(status, "status must not be blank");
        this.sort = sort;
    }

    public void rename(String newName) {
        this.name = requireText(newName, "name must not be blank");
    }

    public void moveTo(Long newParentId) {
        this.parentId = newParentId;
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public int getSort() {
        return sort;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(message);
        }
        return value;
    }
}
