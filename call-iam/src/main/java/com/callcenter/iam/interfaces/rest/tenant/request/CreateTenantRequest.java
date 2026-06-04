package com.callcenter.iam.interfaces.rest.tenant.request;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;

public class CreateTenantRequest {

    @NotBlank
    private String tenantCode;

    @NotBlank
    private String tenantName;

    private LocalDateTime expireTime;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }
}
