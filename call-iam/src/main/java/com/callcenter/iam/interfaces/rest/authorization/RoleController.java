package com.callcenter.iam.interfaces.rest.authorization;

import com.callcenter.iam.application.authorization.AuthorizationRepository;
import com.callcenter.iam.application.authorization.CreateRoleCommand;
import com.callcenter.iam.application.authorization.CreateRoleUseCase;
import com.callcenter.iam.application.authorization.DeleteRoleCommand;
import com.callcenter.iam.application.authorization.DeleteRoleUseCase;
import com.callcenter.iam.application.authorization.RoleDataScope;
import com.callcenter.iam.application.authorization.UpdateRoleCommand;
import com.callcenter.iam.application.authorization.UpdateRoleDataScopeCommand;
import com.callcenter.iam.application.authorization.UpdateRoleDataScopeUseCase;
import com.callcenter.iam.application.authorization.UpdateRolePermissionsCommand;
import com.callcenter.iam.application.authorization.UpdateRolePermissionsUseCase;
import com.callcenter.iam.application.authorization.UpdateRoleUseCase;
import com.callcenter.iam.domain.authorization.Role;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import com.callcenter.iam.interfaces.rest.authorization.request.CreateRoleRequest;
import com.callcenter.iam.interfaces.rest.authorization.request.UpdateRoleDataScopeRequest;
import com.callcenter.iam.interfaces.rest.authorization.request.UpdateRolePermissionsRequest;
import com.callcenter.iam.interfaces.rest.authorization.request.UpdateRoleRequest;
import com.callcenter.iam.interfaces.rest.authorization.response.RoleDataScopeResponse;
import com.callcenter.iam.interfaces.rest.authorization.response.RoleResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/roles")
public class RoleController {

    private final CreateRoleUseCase createRoleUseCase;
    private final UpdateRoleUseCase updateRoleUseCase;
    private final DeleteRoleUseCase deleteRoleUseCase;
    private final UpdateRolePermissionsUseCase updateRolePermissionsUseCase;
    private final UpdateRoleDataScopeUseCase updateRoleDataScopeUseCase;
    private final AuthorizationRepository authorizationRepository;

    public RoleController(
            CreateRoleUseCase createRoleUseCase,
            UpdateRoleUseCase updateRoleUseCase,
            DeleteRoleUseCase deleteRoleUseCase,
            UpdateRolePermissionsUseCase updateRolePermissionsUseCase,
            UpdateRoleDataScopeUseCase updateRoleDataScopeUseCase,
            AuthorizationRepository authorizationRepository
    ) {
        this.createRoleUseCase = createRoleUseCase;
        this.updateRoleUseCase = updateRoleUseCase;
        this.deleteRoleUseCase = deleteRoleUseCase;
        this.updateRolePermissionsUseCase = updateRolePermissionsUseCase;
        this.updateRoleDataScopeUseCase = updateRoleDataScopeUseCase;
        this.authorizationRepository = authorizationRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateRoleRequest request) {
        Role role = createRoleUseCase.execute(new CreateRoleCommand(
                requireTenantId(),
                request.getRoleCode(),
                request.getRoleName(),
                request.getRoleType()
        ));
        return ResponseEntity.ok(envelope(toResponse(role)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<RoleResponse> data = authorizationRepository.findRolesByTenantId(requireTenantId()).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(envelope(data));
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long roleId) {
        return ResponseEntity.ok(envelope(toResponse(requireRole(roleId))));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long roleId, @Valid @RequestBody UpdateRoleRequest request) {
        Role role = updateRoleUseCase.execute(new UpdateRoleCommand(
                requireTenantId(),
                roleId,
                request.getRoleCode(),
                request.getRoleName(),
                request.getRoleType()
        ));
        return ResponseEntity.ok(envelope(toResponse(role)));
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long roleId) {
        Role role = deleteRoleUseCase.execute(new DeleteRoleCommand(requireTenantId(), roleId));
        return ResponseEntity.ok(envelope(toResponse(role)));
    }

    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<Map<String, Object>> updatePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request
    ) {
        Role role = updateRolePermissionsUseCase.execute(new UpdateRolePermissionsCommand(
                requireTenantId(),
                roleId,
                request.getPermissionIds()
        ));
        return ResponseEntity.ok(envelope(toResponse(role)));
    }

    @PutMapping("/{roleId}/data-scope")
    public ResponseEntity<Map<String, Object>> updateDataScope(
            @PathVariable Long roleId,
            @Valid @RequestBody UpdateRoleDataScopeRequest request
    ) {
        Role role = updateRoleDataScopeUseCase.execute(new UpdateRoleDataScopeCommand(
                requireTenantId(),
                roleId,
                request.getScopeType(),
                request.getDepartmentId()
        ));
        return ResponseEntity.ok(envelope(toResponse(role)));
    }

    private Role requireRole(Long roleId) {
        Role role = authorizationRepository.findRoleById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("role not found"));
        if (!requireTenantId().equals(role.getTenantId())) {
            throw new IllegalArgumentException("role tenant mismatch");
        }
        return role;
    }

    private Long requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtTokenProvider.TokenClaims claims = (JwtTokenProvider.TokenClaims) authentication.getDetails();
        if (claims == null || claims.tenantId() == null) {
            throw new IllegalArgumentException("tenant context required");
        }
        return claims.tenantId();
    }

    private RoleResponse toResponse(Role role) {
        List<RoleDataScope> scopes = authorizationRepository.findRoleDataScopes(role.getTenantId(), List.of(role.getId()));
        RoleDataScopeResponse dataScope = scopes.isEmpty()
                ? null
                : new RoleDataScopeResponse(scopes.get(0).scopeType(), scopes.get(0).departmentId());
        return new RoleResponse(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleType(),
                authorizationRepository.findPermissionIdsByRoleId(role.getId()),
                dataScope
        );
    }

    private Map<String, Object> envelope(Object data) {
        return Map.of("success", true, "data", data);
    }
}
