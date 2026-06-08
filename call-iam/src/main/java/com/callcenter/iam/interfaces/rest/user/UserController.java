package com.callcenter.iam.interfaces.rest.user;

import com.callcenter.iam.application.user.AssignUserDepartmentsCommand;
import com.callcenter.iam.application.user.AssignUserDepartmentsUseCase;
import com.callcenter.iam.application.user.AssignUserRolesCommand;
import com.callcenter.iam.application.user.AssignUserRolesUseCase;
import com.callcenter.iam.application.user.CreateUserCommand;
import com.callcenter.iam.application.user.CreateUserUseCase;
import com.callcenter.iam.application.user.DeleteUserCommand;
import com.callcenter.iam.application.user.DeleteUserUseCase;
import com.callcenter.iam.application.user.ResetUserPasswordCommand;
import com.callcenter.iam.application.user.ResetUserPasswordUseCase;
import com.callcenter.iam.application.user.UpdateUserCommand;
import com.callcenter.iam.application.user.UpdateUserStatusCommand;
import com.callcenter.iam.application.user.UpdateUserStatusUseCase;
import com.callcenter.iam.application.user.UpdateUserUseCase;
import com.callcenter.iam.application.user.UserAssignmentRepository;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.infrastructure.security.JwtTokenProvider;
import com.callcenter.iam.interfaces.rest.user.request.AssignUserDepartmentsRequest;
import com.callcenter.iam.interfaces.rest.user.request.AssignUserRolesRequest;
import com.callcenter.iam.interfaces.rest.user.request.CreateUserRequest;
import com.callcenter.iam.interfaces.rest.user.request.ResetUserPasswordRequest;
import com.callcenter.iam.interfaces.rest.user.request.UpdateUserRequest;
import com.callcenter.iam.interfaces.rest.user.request.UpdateUserStatusRequest;
import com.callcenter.iam.interfaces.rest.user.response.CurrentUserProfileResponse;
import com.callcenter.iam.interfaces.rest.user.response.UserResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/users")
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final UpdateUserStatusUseCase updateUserStatusUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;
    private final AssignUserRolesUseCase assignUserRolesUseCase;
    private final AssignUserDepartmentsUseCase assignUserDepartmentsUseCase;
    private final UserRepository userRepository;
    private final UserAssignmentRepository userAssignmentRepository;

    public UserController(
            CreateUserUseCase createUserUseCase,
            UpdateUserUseCase updateUserUseCase,
            DeleteUserUseCase deleteUserUseCase,
            UpdateUserStatusUseCase updateUserStatusUseCase,
            ResetUserPasswordUseCase resetUserPasswordUseCase,
            AssignUserRolesUseCase assignUserRolesUseCase,
            AssignUserDepartmentsUseCase assignUserDepartmentsUseCase,
            UserRepository userRepository,
            UserAssignmentRepository userAssignmentRepository
    ) {
        this.createUserUseCase = createUserUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.updateUserStatusUseCase = updateUserStatusUseCase;
        this.resetUserPasswordUseCase = resetUserPasswordUseCase;
        this.assignUserRolesUseCase = assignUserRolesUseCase;
        this.assignUserDepartmentsUseCase = assignUserDepartmentsUseCase;
        this.userRepository = userRepository;
        this.userAssignmentRepository = userAssignmentRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateUserRequest request) {
        User user = createUserUseCase.execute(new CreateUserCommand(
                requireTenantId(),
                request.getUsername(),
                request.getMobile(),
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        ));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long departmentId) {
        Long tenantId = requireTenantId();
        List<UserResponse> data = (departmentId == null
                ? userRepository.findByTenantId(tenantId)
                : userRepository.findByTenantIdAndDepartmentId(tenantId, departmentId)).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(envelope(data));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        JwtTokenProvider.TokenClaims claims = requireClaims();
        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (claims.tenantId() != null && !claims.tenantId().equals(user.getTenantId())) {
            throw new IllegalArgumentException("user tenant mismatch");
        }
        return ResponseEntity.ok(envelope(new CurrentUserProfileResponse(
                user.getId(),
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname(),
                user.getTenantId(),
                userAssignmentRepository.findRoleIds(user.getId()),
                userAssignmentRepository.findDepartmentIds(user.getId())
        )));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long userId) {
        return ResponseEntity.ok(envelope(toResponse(requireTenantUser(requireTenantId(), userId))));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long userId, @Valid @RequestBody UpdateUserRequest request) {
        User user = updateUserUseCase.execute(new UpdateUserCommand(
                requireTenantId(),
                userId,
                request.getMobile(),
                request.getEmail(),
                request.getNickname()
        ));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long userId) {
        User user = deleteUserUseCase.execute(new DeleteUserCommand(requireTenantId(), userId));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        User user = updateUserStatusUseCase.execute(new UpdateUserStatusCommand(requireTenantId(), userId, request.getStatus()));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    @PutMapping("/{userId}/password/reset")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetUserPasswordRequest request
    ) {
        User user = resetUserPasswordUseCase.execute(new ResetUserPasswordCommand(requireTenantId(), userId, request.getNewPassword()));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<Map<String, Object>> assignRoles(
            @PathVariable Long userId,
            @Valid @RequestBody AssignUserRolesRequest request
    ) {
        User user = assignUserRolesUseCase.execute(new AssignUserRolesCommand(requireTenantId(), userId, request.getRoleIds()));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    @PutMapping("/{userId}/departments")
    public ResponseEntity<Map<String, Object>> assignDepartments(
            @PathVariable Long userId,
            @Valid @RequestBody AssignUserDepartmentsRequest request
    ) {
        User user = assignUserDepartmentsUseCase.execute(new AssignUserDepartmentsCommand(requireTenantId(), userId, request.getDepartmentIds()));
        return ResponseEntity.ok(envelope(toResponse(user)));
    }

    private User requireTenantUser(Long tenantId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new IllegalArgumentException("user tenant mismatch");
        }
        return user;
    }

    private Long requireTenantId() {
        JwtTokenProvider.TokenClaims claims = requireClaims();
        if (claims.tenantId() == null) {
            throw new IllegalArgumentException("tenant context required");
        }
        return claims.tenantId();
    }

    private JwtTokenProvider.TokenClaims requireClaims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtTokenProvider.TokenClaims claims = (JwtTokenProvider.TokenClaims) authentication.getDetails();
        if (claims == null) {
            throw new IllegalArgumentException("authentication required");
        }
        return claims;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getEmail(),
                user.getNickname(),
                user.getStatus().name(),
                userAssignmentRepository.findRoleIds(user.getId()),
                userAssignmentRepository.findDepartmentIds(user.getId())
        );
    }

    private Map<String, Object> envelope(Object data) {
        return Map.of("success", true, "data", data);
    }
}
