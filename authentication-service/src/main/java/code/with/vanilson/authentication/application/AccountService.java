package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.exception.InvalidAccountPasswordException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * AccountService — Application Layer.
 * <p>
 * Self-service account management for the AUTHENTICATED user only (never another user's id —
 * the controller always passes the principal's own id). The JWT subject is the email, so an
 * email change revokes every session and returns a fresh token pair; name-only edits do not.
 * Identity changes are pushed to customer-service asynchronously and fail-open via
 * {@link CustomerProvisioning} — auth never blocks on customer-service.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository       userRepository;
    private final TokenRepository      tokenRepository;
    private final JwtService           jwtService;
    private final PasswordEncoder      passwordEncoder;
    private final RefreshTokenService  refreshTokenService;
    private final CustomerProvisioning customerProvisioning;
    private final MessageSource        messageSource;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long userId) {
        return AccountResponse.from(findUser(userId));
    }

    @Transactional
    public AccountUpdateResponse updateAccount(Long userId, UpdateAccountRequest request) {
        User user = findUser(userId);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.email());

        if (emailChanged) {
            requireValidPassword(request.currentPassword(), user);
            if (userRepository.existsByEmail(request.email())) {
                throw new UserAlreadyExistsException(
                        msg("auth.account.email.taken"), "auth.account.email.taken");
            }
            user.setEmail(request.email());
        }
        user.setFirstname(request.firstname());
        user.setLastname(request.lastname());
        User saved = userRepository.save(user);

        // Fire-and-forget — keeps the customer profile's display identity in sync.
        customerProvisioning.syncCustomerProfile(saved);

        if (!emailChanged) {
            return new AccountUpdateResponse(AccountResponse.from(saved), null);
        }

        // The old email is dead as a JWT subject — kill every session, mint a fresh pair.
        tokenRepository.revokeAllUserTokens(saved.getId());
        String accessJwt  = jwtService.generateAccessToken(saved);
        String refreshJwt = jwtService.generateRefreshToken(saved);
        refreshTokenService.persistTokenPair(saved, accessJwt, refreshJwt);
        log.info("[AccountService] Email changed for userId=[{}] — sessions rotated", saved.getId());

        return new AccountUpdateResponse(AccountResponse.from(saved),
                AuthResponse.of(accessJwt, refreshJwt, String.valueOf(saved.getId()),
                        saved.getEmail(), saved.getRole().name(), saved.getTenantId(),
                        saved.getSellerStatus() != null ? saved.getSellerStatus().name() : null));
    }

    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        requireValidPassword(request.currentPassword(), user);
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidAccountPasswordException(
                    msg("auth.account.password.mismatch"), "auth.account.password.mismatch");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        User saved = userRepository.save(user);

        // Same rule as the reset flow: a password change invalidates every outstanding session.
        tokenRepository.revokeAllUserTokens(saved.getId());
        String accessJwt  = jwtService.generateAccessToken(saved);
        String refreshJwt = jwtService.generateRefreshToken(saved);
        refreshTokenService.persistTokenPair(saved, accessJwt, refreshJwt);
        log.info("[AccountService] Password changed for userId=[{}] — sessions rotated", saved.getId());

        return AuthResponse.of(accessJwt, refreshJwt, String.valueOf(saved.getId()),
                saved.getEmail(), saved.getRole().name(), saved.getTenantId(),
                saved.getSellerStatus() != null ? saved.getSellerStatus().name() : null);
    }

    @Transactional
    public void deleteAccount(Long userId, String rawPassword) {
        User user = findUser(userId);
        requireValidPassword(rawPassword, user);
        softDeleteAndAnonymize(user);
    }

    /**
     * Soft delete + anonymize (GDPR-style): orders/payments keep their history, the real
     * email is freed for future re-registration, and login stays a generic 401.
     * <p>
     * Shared by the self-service flow above (password-proved) and by the admin flow in
     * {@code UserManagementService.deleteUser} (admin authority, no password) — single
     * source of truth for what "deleting a user" means.
     * </p>
     */
    @Transactional
    public void softDeleteAndAnonymize(User user) {
        user.setAccountEnabled(false);
        user.setEmail("deleted-" + user.getId() + "@removed.local");
        user.setFirstname("Deleted");
        user.setLastname("User");
        userRepository.save(user);

        tokenRepository.revokeAllUserTokens(user.getId());
        customerProvisioning.deleteCustomerProfile(user.getId());
        log.info("[AccountService] Account soft-deleted for userId=[{}]", user.getId());
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthUserNotFoundException(
                        "User " + userId + " not found", "auth.user.not.found"));
    }

    private void requireValidPassword(String rawPassword, User user) {
        if (!StringUtils.hasText(rawPassword)
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidAccountPasswordException(
                    msg("auth.account.password.invalid"), "auth.account.password.invalid");
        }
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
