package com.callcenter.iam.interfaces.rest.authorization.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateRoleRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String roleName;

    @NotBlank
    private String roleType;

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }
}
