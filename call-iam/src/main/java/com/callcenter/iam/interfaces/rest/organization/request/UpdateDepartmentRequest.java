package com.callcenter.iam.interfaces.rest.organization.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateDepartmentRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String status;

    private Integer sort;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
