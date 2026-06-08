package com.callcenter.iam.interfaces.rest.auth.request;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    private String tenantCode;

    @NotBlank
    private String account;

    @NotBlank
    private String password;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
