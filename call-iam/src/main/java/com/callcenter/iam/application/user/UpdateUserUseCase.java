package com.callcenter.iam.application.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class UpdateUserUseCase {

    private final UserRepository userRepository;

    public UpdateUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User execute(UpdateUserCommand command) {
        User user = requireTenantUser(command.tenantId(), command.userId());
        if (command.mobile() != null && !command.mobile().isBlank()) {
            userRepository.findByTenantIdAndMobile(command.tenantId(), command.mobile()).ifPresent(existing -> {
                if (!Objects.equals(existing.getId(), command.userId())) {
                    throw new DomainRuleViolationException("mobile already exists");
                }
            });
        }
        if (command.email() != null && !command.email().isBlank()) {
            userRepository.findByTenantIdAndEmail(command.tenantId(), command.email()).ifPresent(existing -> {
                if (!Objects.equals(existing.getId(), command.userId())) {
                    throw new DomainRuleViolationException("email already exists");
                }
            });
        }
        user.updateProfile(command.mobile(), command.email(), command.nickname());
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
