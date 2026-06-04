package com.callcenter.iam.application.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UpdateUserStatusUseCase {

    private final UserRepository userRepository;

    public UpdateUserStatusUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User execute(UpdateUserStatusCommand command) {
        User user = requireTenantUser(command.tenantId(), command.userId());
        switch (command.status()) {
            case "ENABLE" -> user.enable();
            case "DISABLE" -> user.disable();
            case "LOCK" -> user.lock();
            default -> throw new DomainRuleViolationException("unsupported user status");
        }
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
