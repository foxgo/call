package com.callcenter.iam.interfaces.rest.authorization.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateRoleDataScopeRequest {

    @NotBlank
    private String scopeType;

    private Long departmentId;

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }
}
