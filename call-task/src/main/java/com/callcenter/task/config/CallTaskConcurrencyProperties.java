package com.callcenter.task.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "call.task.concurrency")
public class CallTaskConcurrencyProperties {

    @Min(1)
    private int globalMax = 10000;

    @Min(1)
    private int tenantDefaultMax = 1000;

    public int getGlobalMax() {
        return globalMax;
    }

    public void setGlobalMax(int globalMax) {
        this.globalMax = globalMax;
    }

    public int getTenantDefaultMax() {
        return tenantDefaultMax;
    }

    public void setTenantDefaultMax(int tenantDefaultMax) {
        this.tenantDefaultMax = tenantDefaultMax;
    }
}
