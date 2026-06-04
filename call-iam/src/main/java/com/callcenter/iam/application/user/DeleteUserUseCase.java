package com.callcenter.iam.application.user;

import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteUserUseCase {

    private final UserRepository userRepository;
    private final UserAssignmentRepository userAssignmentRepository;

    public DeleteUserUseCase(UserRepository userRepository, UserAssignmentRepository userAssignmentRepository) {
        this.userRepository = userRepository;
        this.userAssignmentRepository = userAssignmentRepository;
    }

    public User execute(DeleteUserCommand command) {
        User user = requireTenantUser(command.tenantId(), command.userId());
        userAssignmentRepository.deleteByUserId(command.userId());
        userRepository.deleteById(command.userId());
        return user;
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
