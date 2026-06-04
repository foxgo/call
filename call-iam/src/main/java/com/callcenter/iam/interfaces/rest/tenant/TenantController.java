package com.callcenter.iam.interfaces.rest.tenant;

import com.callcenter.iam.application.tenant.CreateTenantCommand;
import com.callcenter.iam.application.tenant.CreateTenantUseCase;
import com.callcenter.iam.application.tenant.UpdateTenantCommand;
import com.callcenter.iam.application.tenant.UpdateTenantUseCase;
import com.callcenter.iam.domain.tenant.Tenant;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.interfaces.rest.tenant.request.CreateTenantRequest;
import com.callcenter.iam.interfaces.rest.tenant.request.UpdateTenantRequest;
import com.callcenter.iam.interfaces.rest.tenant.response.TenantResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/iam/tenants")
public class TenantController {

    private final CreateTenantUseCase createTenantUseCase;
    private final UpdateTenantUseCase updateTenantUseCase;
    private final TenantRepository tenantRepository;

    public TenantController(
            CreateTenantUseCase createTenantUseCase,
            UpdateTenantUseCase updateTenantUseCase,
            TenantRepository tenantRepository
    ) {
        this.createTenantUseCase = createTenantUseCase;
        this.updateTenantUseCase = updateTenantUseCase;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = createTenantUseCase.execute(new CreateTenantCommand(
                request.getTenantCode(),
                request.getTenantName(),
                request.getExpireTime()
        ));
        return ResponseEntity.ok(envelope(toResponse(tenant)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<TenantResponse> data = tenantRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(envelope(data));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        return ResponseEntity.ok(envelope(toResponse(tenant)));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long tenantId, @Valid @RequestBody UpdateTenantRequest request) {
        Tenant tenant = updateTenantUseCase.execute(new UpdateTenantCommand(tenantId, request.getTenantName(), request.getExpireTime()));
        return ResponseEntity.ok(envelope(toResponse(tenant)));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        tenant.delete();
        tenantRepository.save(tenant);
        return ResponseEntity.ok(envelope(toResponse(tenant)));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getTenantName(),
                tenant.getStatus().name(),
                tenant.getExpireTime()
        );
    }

    private Map<String, Object> envelope(Object data) {
        return Map.of("success", true, "data", data);
    }
}
