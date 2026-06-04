package com.callcenter.iam.interfaces.rest.audit;

import com.callcenter.iam.domain.audit.AuditLog;
import com.callcenter.iam.infrastructure.audit.AuditLogQuery;
import com.callcenter.iam.infrastructure.audit.AuditLogRepository;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId
    ) {
        List<AuditLogResponse> data = auditLogRepository.query(new AuditLogQuery(
                        requireTenantId(),
                        operatorId,
                        resourceType,
                        resourceId
                )).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/{auditLogId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long auditLogId) {
        AuditLog auditLog = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new IllegalArgumentException("audit log not found"));
        if (!requireTenantId().equals(auditLog.getTenantId())) {
            throw new IllegalArgumentException("audit log tenant mismatch");
        }
        return ResponseEntity.ok(Map.of("success", true, "data", toResponse(auditLog)));
    }

    private Long requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtTokenProvider.TokenClaims claims = (JwtTokenProvider.TokenClaims) authentication.getDetails();
        if (claims == null || claims.tenantId() == null) {
            throw new IllegalArgumentException("tenant context required");
        }
        return claims.tenantId();
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getTenantId(),
                auditLog.getOperatorId(),
                auditLog.getAction(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getCreatedAt()
        );
    }

    public record AuditLogResponse(
            Long id,
            Long tenantId,
            Long operatorId,
            String action,
            String resourceType,
            String resourceId,
            java.time.LocalDateTime createdAt
    ) {
    }
}
