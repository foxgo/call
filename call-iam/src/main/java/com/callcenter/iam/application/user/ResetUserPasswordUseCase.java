package com.callcenter.iam.application.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ResetUserPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ResetUserPasswordUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User execute(ResetUserPasswordCommand command) {
        User user = requireTenantUser(command.tenantId(), command.userId());
        User.passwordPolicy(command.newPassword());
        user.updatePasswordHash(passwordEncoder.encode(command.newPassword()));
        return userRepository.save(user);
    }

    private User requireTenantUser(Long tenantId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainRuleViolationException("user not found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new DomainRuleViolationException("user tenant mismatch");
        }
        return user;
    }
}
