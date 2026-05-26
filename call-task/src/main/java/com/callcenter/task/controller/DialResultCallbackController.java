package com.callcenter.task.controller;

import com.callcenter.task.model.DialResultCallbackRequest;
import com.callcenter.task.service.DialResultWritebackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/{tenantId}/tasks/callbacks")
public class DialResultCallbackController {

    private final DialResultWritebackService dialResultWritebackService;

    public DialResultCallbackController(DialResultWritebackService dialResultWritebackService) {
        this.dialResultWritebackService = dialResultWritebackService;
    }

    @PostMapping("/dial-result")
    public ResponseEntity<Void> handleDialResult(
            @PathVariable Long tenantId,
            @Valid @RequestBody DialResultCallbackRequest request
    ) {
        dialResultWritebackService.handleCallback(tenantId, request);
        return ResponseEntity.ok().build();
    }
}
