package com.callcenter.iam.interfaces.rest.authorization.request;

import java.util.List;

public class UpdateRolePermissionsRequest {

    private List<Long> permissionIds;

    public List<Long> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<Long> permissionIds) {
        this.permissionIds = permissionIds;
    }
}
