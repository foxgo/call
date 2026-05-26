package com.callcenter.task.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ImportDialUnitItem {

    @NotBlank
    @Size(max = 32)
    private String phone;

    @Size(max = 128)
    private String bizIdempotencyKey = "";

    private Float score = 0F;

    private Integer maxRetryCount = 3;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBizIdempotencyKey() {
        return bizIdempotencyKey;
    }

    public void setBizIdempotencyKey(String bizIdempotencyKey) {
        this.bizIdempotencyKey = bizIdempotencyKey;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
}
