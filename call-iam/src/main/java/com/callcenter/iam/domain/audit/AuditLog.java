package com.callcenter.iam.domain.audit;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.time.LocalDateTime;
import java.util.Objects;

public class AuditLog {

    private final Long id;
    private final Long tenantId;
    private final Long operatorId;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final LocalDateTime createdAt;

    public AuditLog(Long id, Long tenantId, Long operatorId, String action, String resourceType, String resourceId, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = tenantId;
        this.operatorId = operatorId;
        this.action = requireText(action, "action must not be blank");
        this.resourceType = requireText(resourceType, "resourceType must not be blank");
        this.resourceId = resourceId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(message);
        }
        return value;
    }
}
