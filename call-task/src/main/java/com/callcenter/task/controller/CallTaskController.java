package com.callcenter.task.controller;

import com.callcenter.task.model.CreateTaskRequest;
import com.callcenter.task.model.TaskSummaryResponse;
import com.callcenter.task.service.CallTaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/{tenantId}/tasks")
public class CallTaskController {

    private final CallTaskService callTaskService;

    public CallTaskController(CallTaskService callTaskService) {
        this.callTaskService = callTaskService;
    }

    @PostMapping
    public TaskSummaryResponse createTask(
            @PathVariable Long tenantId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        return callTaskService.createTask(tenantId, request);
    }

    @PostMapping("/{taskId}/start")
    public TaskSummaryResponse startTask(@PathVariable Long tenantId, @PathVariable Long taskId) {
        return callTaskService.startTask(tenantId, taskId);
    }

    @PostMapping("/{taskId}/pause")
    public TaskSummaryResponse pauseTask(@PathVariable Long tenantId, @PathVariable Long taskId) {
        return callTaskService.pauseTask(tenantId, taskId);
    }

    @PostMapping("/{taskId}/resume")
    public TaskSummaryResponse resumeTask(@PathVariable Long tenantId, @PathVariable Long taskId) {
        return callTaskService.resumeTask(tenantId, taskId);
    }

    @GetMapping("/{taskId}")
    public TaskSummaryResponse getTask(@PathVariable Long tenantId, @PathVariable Long taskId) {
        return callTaskService.getTask(tenantId, taskId);
    }

    @GetMapping
    public List<TaskSummaryResponse> listTasks(@PathVariable Long tenantId) {
        return callTaskService.listTasks(tenantId);
    }
}
