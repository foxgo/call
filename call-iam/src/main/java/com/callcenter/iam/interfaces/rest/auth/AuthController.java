package com.callcenter.iam.interfaces.rest.auth;

import com.callcenter.iam.application.auth.AuthenticationFailedException;
import com.callcenter.iam.application.auth.LoginCommand;
import com.callcenter.iam.application.auth.LoginResult;
import com.callcenter.iam.application.auth.LoginUseCase;
import com.callcenter.iam.application.user.UserAssignmentRepository;
import com.callcenter.iam.domain.tenant.TenantRepository;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.interfaces.rest.auth.request.LoginRequest;
import com.callcenter.iam.interfaces.rest.auth.request.RefreshTokenRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserAssignmentRepository userAssignmentRepository;

    public AuthController(
            LoginUseCase loginUseCase,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            UserAssignmentRepository userAssignmentRepository
    ) {
        this.loginUseCase = loginUseCase;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userAssignmentRepository = userAssignmentRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Long tenantId = resolveTenantId(request.getTenantCode());
        Optional<User> user = findUser(tenantId, request.getAccount());
        LoginResult result = loginUseCase.login(new LoginCommand(
                tenantId,
                request.getAccount(),
                request.getPassword(),
                user.map(foundUser -> userAssignmentRepository.findRoleIds(foundUser.getId())).orElse(List.of()),
                user.map(foundUser -> userAssignmentRepository.findDepartmentIds(foundUser.getId())).orElse(List.of())
        ));
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResult result = loginUseCase.refresh(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("success", true, "data", result));
    }

    private Long resolveTenantId(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return null;
        }
        return tenantRepository.findByTenantCode(tenantCode)
                .map(tenant -> tenant.getId())
                .orElseThrow(() -> new AuthenticationFailedException("invalid credentials"));
    }

    private Optional<User> findUser(Long tenantId, String account) {
        if (account == null || account.isBlank()) {
            return Optional.empty();
        }
        if (account.contains("@")) {
            return userRepository.findByTenantIdAndEmail(tenantId, account);
        }
        if (account.chars().allMatch(Character::isDigit)) {
            return userRepository.findByTenantIdAndMobile(tenantId, account);
        }
        return userRepository.findByTenantIdAndUsername(tenantId, account);
    }
}
