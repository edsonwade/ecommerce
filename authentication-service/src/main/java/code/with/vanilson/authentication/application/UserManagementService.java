package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.AuditLog;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.infrastructure.AuditLogRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository     userRepo;
    private final AuditLogRepository auditRepo;

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(Pageable pageable) {
        return userRepo.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public UserSummaryResponse updateRole(String actorEmail, Long actorId,
                                          Long targetUserId, Role newRole) {
        if (actorId != null && actorId.equals(targetUserId)) {
            throw new IllegalArgumentException("Admin cannot change own role");
        }

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        "User " + targetUserId + " not found", "auth.user.not.found"));

        Role previous = target.getRole();
        if (previous == newRole) {
            return toResponse(target);
        }

        target.setRole(newRole);
        userRepo.save(target);

        auditRepo.save(AuditLog.builder()
                .changedBy(actorEmail)
                .targetUserId(String.valueOf(targetUserId))
                .previousRole(previous)
                .newRole(newRole)
                .build());

        log.info("[UserManagement] Role changed: actor={} target={} {} -> {}",
                actorEmail, targetUserId, previous, newRole);

        return toResponse(target);
    }

    private UserSummaryResponse toResponse(User u) {
        return new UserSummaryResponse(
                u.getId(), u.getEmail(), u.getFirstname(), u.getLastname(),
                u.getRole(), u.getTenantId(), u.isAccountEnabled());
    }
}
