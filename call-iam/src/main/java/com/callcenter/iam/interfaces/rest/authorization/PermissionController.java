package com.callcenter.iam.interfaces.rest.authorization;

import com.callcenter.iam.application.authorization.AuthorizationRepository;
import com.callcenter.iam.interfaces.rest.authorization.response.PermissionResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/permissions")
public class PermissionController {

    private final AuthorizationRepository authorizationRepository;

    public PermissionController(AuthorizationRepository authorizationRepository) {
        this.authorizationRepository = authorizationRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<PermissionResponse> data = authorizationRepository.findAllPermissions().stream()
                .map(permission -> new PermissionResponse(
                        permission.getId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName()
                ))
                .toList();
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}
