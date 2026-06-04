package com.callcenter.iam.interfaces.rest.user.request;

import java.util.List;

public class AssignUserDepartmentsRequest {

    private List<Long> departmentIds;

    public List<Long> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(List<Long> departmentIds) {
        this.departmentIds = departmentIds;
    }
}
