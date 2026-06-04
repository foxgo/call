package com.callcenter.iam.interfaces.rest.user.request;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserStatusRequest {

    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
