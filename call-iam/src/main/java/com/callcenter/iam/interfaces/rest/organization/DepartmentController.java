package com.callcenter.iam.interfaces.rest.organization;

import com.callcenter.iam.application.organization.CreateDepartmentCommand;
import com.callcenter.iam.application.organization.CreateDepartmentUseCase;
import com.callcenter.iam.application.organization.DeleteDepartmentCommand;
import com.callcenter.iam.application.organization.DeleteDepartmentUseCase;
import com.callcenter.iam.application.organization.MoveDepartmentCommand;
import com.callcenter.iam.application.organization.MoveDepartmentUseCase;
import com.callcenter.iam.application.organization.UpdateDepartmentCommand;
import com.callcenter.iam.application.organization.UpdateDepartmentUseCase;
import com.callcenter.iam.domain.organization.Department;
import com.callcenter.iam.domain.organization.DepartmentRepository;
import com.callcenter.iam.interfaces.rest.organization.request.CreateDepartmentRequest;
import com.callcenter.iam.interfaces.rest.organization.request.MoveDepartmentRequest;
import com.callcenter.iam.interfaces.rest.organization.request.UpdateDepartmentRequest;
import com.callcenter.iam.interfaces.rest.organization.response.DepartmentResponse;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
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
@RequestMapping("/api/iam/departments")
public class DepartmentController {

    private final CreateDepartmentUseCase createDepartmentUseCase;
    private final UpdateDepartmentUseCase updateDepartmentUseCase;
    private final MoveDepartmentUseCase moveDepartmentUseCase;
    private final DeleteDepartmentUseCase deleteDepartmentUseCase;
    private final DepartmentRepository departmentRepository;

    public DepartmentController(
            CreateDepartmentUseCase createDepartmentUseCase,
            UpdateDepartmentUseCase updateDepartmentUseCase,
            MoveDepartmentUseCase moveDepartmentUseCase,
            DeleteDepartmentUseCase deleteDepartmentUseCase,
            DepartmentRepository departmentRepository
    ) {
        this.createDepartmentUseCase = createDepartmentUseCase;
        this.updateDepartmentUseCase = updateDepartmentUseCase;
        this.moveDepartmentUseCase = moveDepartmentUseCase;
        this.deleteDepartmentUseCase = deleteDepartmentUseCase;
        this.departmentRepository = departmentRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateDepartmentRequest request) {
        Department department = createDepartmentUseCase.execute(new CreateDepartmentCommand(
                null,
                requireTenantId(),
                request.getParentId(),
                request.getName(),
                request.getStatus(),
                request.getSort() == null ? 0 : request.getSort()
        ));
        return ResponseEntity.ok(envelope(toResponse(department)));
    }

    @GetMapping("/tree")
    public ResponseEntity<Map<String, Object>> tree() {
        List<DepartmentTreeNodeResponse> data = buildTree(departmentRepository.findByTenantId(requireTenantId()));
        return ResponseEntity.ok(envelope(data));
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long departmentId) {
        Department department = requireDepartment(departmentId, requireTenantId());
        return ResponseEntity.ok(envelope(toResponse(department)));
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        Department department = updateDepartmentUseCase.execute(new UpdateDepartmentCommand(
                requireTenantId(),
                departmentId,
                request.getName(),
                request.getStatus(),
                request.getSort() == null ? 0 : request.getSort()
        ));
        return ResponseEntity.ok(envelope(toResponse(department)));
    }

    @PutMapping("/{departmentId}/move")
    public ResponseEntity<Map<String, Object>> move(
            @PathVariable Long departmentId,
            @Valid @RequestBody MoveDepartmentRequest request
    ) {
        Department department = moveDepartmentUseCase.execute(new MoveDepartmentCommand(
                requireTenantId(),
                departmentId,
                request.getParentId()
        ));
        return ResponseEntity.ok(envelope(toResponse(department)));
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long departmentId) {
        Department department = deleteDepartmentUseCase.execute(new DeleteDepartmentCommand(requireTenantId(), departmentId));
        return ResponseEntity.ok(envelope(toResponse(department)));
    }

    private List<DepartmentTreeNodeResponse> buildTree(List<Department> departments) {
        Map<Long, DepartmentTreeNodeResponse> nodeById = new HashMap<>();
        List<DepartmentTreeNodeResponse> roots = new ArrayList<>();
        for (Department department : departments) {
            nodeById.put(department.getId(), new DepartmentTreeNodeResponse(
                    department.getId(),
                    department.getParentId(),
                    department.getName(),
                    department.getStatus(),
                    department.getSort(),
                    new ArrayList<>()
            ));
        }
        for (DepartmentTreeNodeResponse node : nodeById.values()) {
            if (node.parentId() == null) {
                roots.add(node);
                continue;
            }
            DepartmentTreeNodeResponse parent = nodeById.get(node.parentId());
            if (parent != null) {
                parent.children().add(node);
            } else {
                roots.add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<DepartmentTreeNodeResponse> nodes) {
        nodes.sort(java.util.Comparator.comparing(DepartmentTreeNodeResponse::sort).thenComparing(DepartmentTreeNodeResponse::id));
        for (DepartmentTreeNodeResponse node : nodes) {
            sortTree(node.children());
        }
    }

    private Long requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtTokenProvider.TokenClaims claims = (JwtTokenProvider.TokenClaims) authentication.getDetails();
        if (claims == null || claims.tenantId() == null) {
            throw new IllegalArgumentException("tenant context required");
        }
        return claims.tenantId();
    }

    private Department requireDepartment(Long departmentId, Long tenantId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("department not found"));
        if (!tenantId.equals(department.getTenantId())) {
            throw new IllegalArgumentException("department tenant mismatch");
        }
        return department;
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getParentId(),
                department.getName(),
                department.getStatus(),
                department.getSort()
        );
    }

    private Map<String, Object> envelope(Object data) {
        return Map.of("success", true, "data", data);
    }

    public record DepartmentTreeNodeResponse(
            Long id,
            Long parentId,
            String name,
            String status,
            int sort,
            List<DepartmentTreeNodeResponse> children
    ) {
    }
}
