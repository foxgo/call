package com.callcenter.task.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "call.task.capacity")
public class CallTaskCapacityControlProperties {

    private Duration controlInterval = Duration.ofSeconds(10);
    private Duration metricsInterval = Duration.ofSeconds(5);
    private Duration cooldown = Duration.ofSeconds(30);

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double deadbandRatio = 0.05d;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double maxAdjustRatio = 0.10d;

    private String poolKey = "ai-default";

    @Min(1)
    private int poolHardMax = 1000;

    @Min(1)
    private int taskMinTarget = 1;

    @Min(1)
    private int taskBaseShare = 1;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double ewmaAlpha = 0.25d;

    public Duration getControlInterval() {
        return controlInterval;
    }

    public void setControlInterval(Duration controlInterval) {
        this.controlInterval = controlInterval;
    }

    public Duration getMetricsInterval() {
        return metricsInterval;
    }

    public void setMetricsInterval(Duration metricsInterval) {
        this.metricsInterval = metricsInterval;
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public void setCooldown(Duration cooldown) {
        this.cooldown = cooldown;
    }

    public double getDeadbandRatio() {
        return deadbandRatio;
    }

    public void setDeadbandRatio(double deadbandRatio) {
        this.deadbandRatio = deadbandRatio;
    }

    public double getMaxAdjustRatio() {
        return maxAdjustRatio;
    }

    public void setMaxAdjustRatio(double maxAdjustRatio) {
        this.maxAdjustRatio = maxAdjustRatio;
    }

    public String getPoolKey() {
        return poolKey;
    }

    public void setPoolKey(String poolKey) {
        this.poolKey = poolKey;
    }

    public int getPoolHardMax() {
        return poolHardMax;
    }

    public void setPoolHardMax(int poolHardMax) {
        this.poolHardMax = poolHardMax;
    }

    public int getTaskMinTarget() {
        return taskMinTarget;
    }

    public void setTaskMinTarget(int taskMinTarget) {
        this.taskMinTarget = taskMinTarget;
    }

    public int getTaskBaseShare() {
        return taskBaseShare;
    }

    public void setTaskBaseShare(int taskBaseShare) {
        this.taskBaseShare = taskBaseShare;
    }

    public double getEwmaAlpha() {
        return ewmaAlpha;
    }

    public void setEwmaAlpha(double ewmaAlpha) {
        this.ewmaAlpha = ewmaAlpha;
    }
}
