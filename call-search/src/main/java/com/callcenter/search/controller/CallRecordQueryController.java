package com.callcenter.search.controller;

import com.callcenter.search.model.CallRecordDetailView;
import com.callcenter.search.model.CallRecordQueryRequest;
import com.callcenter.search.model.CallRecordView;
import com.callcenter.search.model.PageResponse;
import com.callcenter.search.service.CallRecordQueryService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/{tenantId}/call-records")
public class CallRecordQueryController {

    private final CallRecordQueryService queryService;

    public CallRecordQueryController(CallRecordQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PageResponse<CallRecordView> query(
            @PathVariable long tenantId,
            @Valid @ModelAttribute CallRecordQueryRequest request
    ) {
        return queryService.query(tenantId, request);
    }

    @GetMapping("/{callId}")
    public CallRecordDetailView detail(
            @PathVariable long tenantId,
            @PathVariable String callId
    ) {
        return queryService.detail(tenantId, callId);
    }
}
