package com.callcenter.task.controller;

import com.callcenter.task.model.ImportBatchResponse;
import com.callcenter.task.model.ImportDialUnitsRequest;
import com.callcenter.task.service.CallTaskImportService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/{tenantId}/tasks/{taskId}/imports")
public class CallTaskImportController {

    private final CallTaskImportService callTaskImportService;

    public CallTaskImportController(CallTaskImportService callTaskImportService) {
        this.callTaskImportService = callTaskImportService;
    }

    @PostMapping
    public ImportBatchResponse importDialUnits(
            @PathVariable Long tenantId,
            @PathVariable Long taskId,
            @Valid @RequestBody ImportDialUnitsRequest request
    ) {
        return callTaskImportService.importDialUnits(tenantId, taskId, request);
    }

    @GetMapping("/{importBatchId}")
    public ImportBatchResponse getImportBatch(
            @PathVariable Long tenantId,
            @PathVariable Long taskId,
            @PathVariable Long importBatchId
    ) {
        return callTaskImportService.getImportBatch(tenantId, taskId, importBatchId);
    }
}
