package com.callcenter.iam.application.user;

import com.callcenter.iam.application.audit.AuditCommand;
import com.callcenter.iam.domain.shared.DomainRuleViolationException;
import com.callcenter.iam.domain.user.User;
import com.callcenter.iam.domain.user.UserRepository;
import com.callcenter.iam.domain.user.UserType;
import com.callcenter.iam.infrastructure.audit.AuditEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventPublisher auditEventPublisher;

    public CreateUserUseCase(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditEventPublisher auditEventPublisher
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventPublisher = auditEventPublisher;
    }

    public User execute(CreateUserCommand command) {
        if (userRepository.findByTenantIdAndUsername(command.tenantId(), command.username()).isPresent()) {
            throw new DomainRuleViolationException("username already exists");
        }
        if (command.mobile() != null && !command.mobile().isBlank()
                && userRepository.findByTenantIdAndMobile(command.tenantId(), command.mobile()).isPresent()) {
            throw new DomainRuleViolationException("mobile already exists");
        }
        if (command.email() != null && !command.email().isBlank()
                && userRepository.findByTenantIdAndEmail(command.tenantId(), command.email()).isPresent()) {
            throw new DomainRuleViolationException("email already exists");
        }
        User.passwordPolicy(command.password());
        long userId = userRepository.findAll().stream()
                .mapToLong(User::getId)
                .max()
                .orElse(0L) + 1;
        User user = User.createWithPasswordHash(
                userId,
                command.tenantId(),
                UserType.TENANT,
                command.username(),
                command.mobile(),
                command.email(),
                passwordEncoder.encode(command.password()),
                command.nickname()
        );
        User saved = userRepository.save(user);
        auditEventPublisher.publish(new AuditCommand(
                command.tenantId(),
                command.operatorId(),
                "USER_CREATED",
                "USER",
                String.valueOf(saved.getId())
        ));
        return saved;
    }
}
