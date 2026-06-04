package com.callcenter.iam.domain.tenant;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import java.time.LocalDateTime;
import java.util.Objects;

public class Tenant {

    private final Long id;
    private final String tenantCode;
    private String tenantName;
    private TenantStatus status;
    private LocalDateTime expireTime;

    private Tenant(Long id, String tenantCode, String tenantName, TenantStatus status, LocalDateTime expireTime) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantCode = requireText(tenantCode, "tenantCode must not be blank");
        this.tenantName = requireText(tenantName, "tenantName must not be blank");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.expireTime = expireTime;
    }

    public static Tenant active(Long id, String tenantCode, String tenantName, LocalDateTime expireTime) {
        return new Tenant(id, tenantCode, tenantName, TenantStatus.ACTIVE, expireTime);
    }

    public static Tenant suspended(Long id, String tenantCode, String tenantName, LocalDateTime expireTime) {
        return new Tenant(id, tenantCode, tenantName, TenantStatus.SUSPENDED, expireTime);
    }

    public static Tenant deleted(Long id, String tenantCode, String tenantName) {
        return new Tenant(id, tenantCode, tenantName, TenantStatus.DELETED, null);
    }

    public void suspend() {
        if (status != TenantStatus.ACTIVE) {
            throw new DomainRuleViolationException("only active tenant can be suspended");
        }
        status = TenantStatus.SUSPENDED;
    }

    public void expire() {
        if (status == TenantStatus.DELETED) {
            throw new DomainRuleViolationException("deleted tenant cannot expire");
        }
        status = TenantStatus.EXPIRED;
    }

    public void delete() {
        status = TenantStatus.DELETED;
    }

    public void reactivate() {
        if (status == TenantStatus.DELETED) {
            throw new DomainRuleViolationException("deleted tenant cannot be reactivated");
        }
        if (status == TenantStatus.EXPIRED) {
            throw new DomainRuleViolationException("expired tenant cannot be reactivated directly");
        }
        status = TenantStatus.ACTIVE;
    }

    public void updateBasics(String newTenantName, LocalDateTime newExpireTime) {
        this.tenantName = requireText(newTenantName, "tenantName must not be blank");
        this.expireTime = newExpireTime;
    }

    public Long getId() {
        return id;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException(message);
        }
        return value;
    }
}
