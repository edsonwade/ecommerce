package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.AuditLog;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.SellerStatus;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AdminActionNotAllowedException;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.AuditLogRepository;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository       userRepo;
    private final AuditLogRepository   auditRepo;
    private final PasswordEncoder      passwordEncoder;
    private final CustomerProvisioning customerProvisioning;
    private final TokenRepository      tokenRepository;
    private final AccountService       accountService;
    private final MessageSource        messageSource;

    @Transactional(readOnly = true)
    public Page<UserSummaryResponse> listUsers(Pageable pageable) {
        return userRepo.findAll(pageable).map(this::toResponse);
    }

    /**
     * Creates a user with any role — ADMIN-only entry point (POST /api/v1/auth/users).
     * <p>
     * Public self-registration ({@code AuthService.register}) stays untouched and keeps
     * blocking ADMIN; this path is the deliberate exception, guarded at the controller by
     * {@code hasRole('ADMIN')}. The customer-profile side-effect is the same fire-and-forget
     * used by registration — it never blocks the admin call.
     * </p>
     * <p>
     * Audit: {@code role_audit_log} has NOT NULL previous/new role columns, so a creation is
     * recorded with previousRole == newRole == the assigned role (a dedicated action column
     * is a future enhancement — the row still answers who created whom, and when).
     * </p>
     */
    @Transactional
    public UserSummaryResponse createUser(String actorEmail, AdminCreateUserRequest request) {
        if (userRepo.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(
                    msg("auth.user.already.exists", request.email()),
                    "auth.user.already.exists");
        }

        User user = User.builder()
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .tenantId(StringUtils.hasText(request.tenantId()) ? request.tenantId() : "default")
                // Admin-created sellers skip the approval queue — the admin IS the approver.
                .sellerStatus(request.role() == Role.SELLER ? SellerStatus.APPROVED : null)
                .accountEnabled(true)
                .accountLocked(false)
                .build();

        User saved = userRepo.save(user);

        customerProvisioning.ensureCustomerProfile(saved);

        auditRepo.save(AuditLog.builder()
                .changedBy(actorEmail)
                .targetUserId(String.valueOf(saved.getId()))
                .previousRole(saved.getRole())
                .newRole(saved.getRole())
                .build());

        log.info("[UserManagement] User created by admin: actor={} targetId={} role={}",
                actorEmail, saved.getId(), saved.getRole());

        return toResponse(saved);
    }

    /**
     * Partially updates another user's identity (name/email) — ADMIN-only entry point
     * (PATCH /api/v1/auth/users/{userId}).
     * <p>
     * Mirrors the self-service {@code AccountService.updateAccount} semantics without the
     * password proof (admin authority): an email change frees the old address, revokes every
     * session of the target (the JWT subject is the email) and the display identity is pushed
     * to customer-service fire-and-forget.
     * </p>
     */
    @Transactional
    public UserSummaryResponse updateUser(String actorEmail, Long targetUserId,
                                          AdminUpdateUserRequest request) {
        if (!StringUtils.hasText(request.firstname())
                && !StringUtils.hasText(request.lastname())
                && !StringUtils.hasText(request.email())) {
            throw new IllegalArgumentException("At least one of firstname, lastname, email is required");
        }

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        msg("auth.user.not.found", targetUserId), "auth.user.not.found"));

        boolean emailChanged = StringUtils.hasText(request.email())
                && !target.getEmail().equalsIgnoreCase(request.email());
        if (emailChanged) {
            if (userRepo.existsByEmail(request.email())) {
                throw new UserAlreadyExistsException(
                        msg("auth.account.email.taken"), "auth.account.email.taken");
            }
            target.setEmail(request.email());
        }
        if (StringUtils.hasText(request.firstname())) {
            target.setFirstname(request.firstname());
        }
        if (StringUtils.hasText(request.lastname())) {
            target.setLastname(request.lastname());
        }

        User saved = userRepo.save(target);

        customerProvisioning.syncCustomerProfile(saved);

        if (emailChanged) {
            // The old email is dead as a JWT subject — kill every session of the target.
            tokenRepository.revokeAllUserTokens(saved.getId());
        }

        auditRepo.save(AuditLog.builder()
                .changedBy(actorEmail)
                .targetUserId(String.valueOf(saved.getId()))
                .previousRole(saved.getRole())
                .newRole(saved.getRole())
                .build());

        log.info("[UserManagement] User updated by admin: actor={} targetId={} emailChanged={}",
                actorEmail, saved.getId(), emailChanged);

        return toResponse(saved);
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

    /**
     * Activates or deactivates a user's account — ADMIN-only entry point
     * (PATCH /api/v1/auth/users/{userId}/status).
     * <p>
     * Deactivation revokes every refresh/access token so the lockout takes effect on the
     * next request, not on token expiry. An admin cannot deactivate their own account
     * (same spirit as the self-demotion guard on {@code updateRole}).
     * </p>
     */
    @Transactional
    public UserSummaryResponse setUserStatus(String actorEmail, Long actorId,
                                             Long targetUserId, boolean enabled) {
        if (!enabled && actorId != null && actorId.equals(targetUserId)) {
            throw new AdminActionNotAllowedException(
                    msg("auth.admin.self.deactivate.denied"), "auth.admin.self.deactivate.denied");
        }

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        msg("auth.user.not.found", targetUserId), "auth.user.not.found"));

        if (target.isAccountEnabled() == enabled) {
            return toResponse(target);
        }

        target.setAccountEnabled(enabled);
        User saved = userRepo.save(target);

        if (!enabled) {
            tokenRepository.revokeAllUserTokens(saved.getId());
        }

        auditRepo.save(AuditLog.builder()
                .changedBy(actorEmail)
                .targetUserId(String.valueOf(saved.getId()))
                .previousRole(saved.getRole())
                .newRole(saved.getRole())
                .build());

        log.info("[UserManagement] User status changed by admin: actor={} targetId={} enabled={}",
                actorEmail, saved.getId(), enabled);

        return toResponse(saved);
    }

    /**
     * Approves, suspends or re-queues a SELLER — ADMIN-only entry point
     * (PATCH /api/v1/auth/users/{userId}/seller-status).
     * <p>
     * SUSPENDED revokes every refresh/access token so the (stale) sellerStatus JWT claim
     * cannot outlive the suspension beyond the access token's own TTL (design decision D1).
     * APPROVED / PENDING_APPROVAL never revoke — the seller keeps their session and the
     * new status takes effect on their next login/refresh.
     * </p>
     */
    @Transactional
    public UserSummaryResponse setSellerStatus(String actorEmail, Long targetUserId,
                                               SellerStatus status) {
        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        msg("auth.user.not.found", targetUserId), "auth.user.not.found"));

        if (target.getRole() != Role.SELLER) {
            throw new AdminActionNotAllowedException(
                    msg("auth.seller.status.not.seller", targetUserId),
                    "auth.seller.status.not.seller");
        }

        if (target.getSellerStatus() == status) {
            return toResponse(target);
        }

        target.setSellerStatus(status);
        User saved = userRepo.save(target);

        if (status == SellerStatus.SUSPENDED) {
            tokenRepository.revokeAllUserTokens(saved.getId());
        }

        auditRepo.save(AuditLog.builder()
                .changedBy(actorEmail)
                .targetUserId(String.valueOf(saved.getId()))
                .previousRole(saved.getRole())
                .newRole(saved.getRole())
                .build());

        log.info("[UserManagement] Seller status changed by admin: actor={} targetId={} status={}",
                actorEmail, saved.getId(), status);

        return toResponse(saved);
    }

    /**
     * Soft-deletes a user — ADMIN-only entry point (DELETE /api/v1/auth/users/{userId}).
     * Delegates to {@code AccountService.softDeleteAndAnonymize} (single source of truth,
     * same behaviour as self-service deletion) without requiring the target's password.
     * Deleting a SELLER does not touch their products — the admin products page is the
     * tool for that (documented design decision).
     */
    @Transactional
    public void deleteUser(String actorEmail, Long actorId, Long targetUserId) {
        if (actorId != null && actorId.equals(targetUserId)) {
            throw new AdminActionNotAllowedException(
                    msg("auth.admin.self.delete.denied"), "auth.admin.self.delete.denied");
        }

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        msg("auth.user.not.found", targetUserId), "auth.user.not.found"));

        Role role = target.getRole();
        accountService.softDeleteAndAnonymize(target);

        auditRepo.save(AuditLog.builder()
                .changedBy(actorEmail)
                .targetUserId(String.valueOf(target.getId()))
                .previousRole(role)
                .newRole(role)
                .build());

        log.info("[UserManagement] User soft-deleted by admin: actor={} targetId={}",
                actorEmail, target.getId());
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private UserSummaryResponse toResponse(User u) {
        return new UserSummaryResponse(
                u.getId(), u.getEmail(), u.getFirstname(), u.getLastname(),
                u.getRole(), u.getTenantId(), u.isAccountEnabled(), u.getSellerStatus());
    }
}
