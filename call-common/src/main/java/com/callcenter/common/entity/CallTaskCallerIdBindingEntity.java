package com.callcenter.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("call_task_caller_id_binding")
public class CallTaskCallerIdBindingEntity {

    @TableId
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long callerIdId;
    private String bindingType;
    private Integer priorityBoost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getCallerIdId() {
        return callerIdId;
    }

    public void setCallerIdId(Long callerIdId) {
        this.callerIdId = callerIdId;
    }

    public String getBindingType() {
        return bindingType;
    }

    public void setBindingType(String bindingType) {
        this.bindingType = bindingType;
    }

    public Integer getPriorityBoost() {
        return priorityBoost;
    }

    public void setPriorityBoost(Integer priorityBoost) {
        this.priorityBoost = priorityBoost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
