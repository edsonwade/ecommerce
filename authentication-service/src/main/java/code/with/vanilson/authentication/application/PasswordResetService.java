package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.PasswordResetToken;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidPasswordResetTokenException;
import code.with.vanilson.authentication.infrastructure.PasswordResetTokenRepository;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import code.with.vanilson.authentication.infrastructure.email.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * PasswordResetService — Application Layer.
 * <p>
 * Orchestrates the forgot-password flow for ALL roles (it keys off email, never role):
 * <ol>
 *   <li>{@link #requestReset} — issues a single-use, time-boxed token and emails its link.
 *       Always returns silently regardless of whether the email exists (no user enumeration).</li>
 *   <li>{@link #resetPassword} — verifies the token, sets the new BCrypt password, consumes the
 *       token, and revokes every existing JWT session so old tokens cannot outlive the change.</li>
 * </ol>
 * Only the SHA-256 hash of the token is persisted; the raw token lives only in the email link.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Slf4j
@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int          TOKEN_BYTES   = 32; // 256 bits of entropy

    private final UserRepository               userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final TokenRepository              jwtTokenRepository;
    private final PasswordEncoder              passwordEncoder;
    private final EmailSender                  emailSender;
    private final MessageSource                messageSource;

    @Value("${app.password-reset.token-ttl:PT30M}")
    private Duration tokenTtl;

    /** Base URL of the frontend reset page; the raw token is appended as ?token=…  */
    @Value("${app.password-reset.reset-url-base:http://localhost:8080/reset-password}")
    private String resetUrlBase;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                TokenRepository jwtTokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailSender emailSender,
                                MessageSource messageSource) {
        this.userRepository     = userRepository;
        this.tokenRepository    = tokenRepository;
        this.jwtTokenRepository = jwtTokenRepository;
        this.passwordEncoder    = passwordEncoder;
        this.emailSender        = emailSender;
        this.messageSource      = messageSource;
    }

    // -------------------------------------------------------
    // Request a reset link
    // -------------------------------------------------------

    /**
     * Issues a reset token for the account and emails the link. Fail-open and enumeration-safe:
     * if no active account matches, it logs and returns normally so the caller can respond with
     * the same constant 200 it returns on success.
     */
    @Transactional
    public void requestReset(String email) {
        log.info("[PasswordResetService] Reset requested for email=[{}]", email);

        Optional<User> maybeUser = userRepository.findActiveUserByEmail(email);
        if (maybeUser.isEmpty()) {
            // Do NOT reveal that the address is unknown — same response as the happy path.
            log.info("[PasswordResetService] No active account for email=[{}] — silently ignoring", email);
            return;
        }
        User user = maybeUser.get();

        LocalDateTime now = LocalDateTime.now();
        // One live link at a time — burn any still-usable tokens for this user.
        tokenRepository.invalidateActiveTokensForUser(user.getId(), now);

        String rawToken = generateRawToken();
        PasswordResetToken entity = PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(now.plus(tokenTtl))
                .createdAt(now)
                .build();
        tokenRepository.save(entity);

        String resetLink = resetUrlBase + "?token=" + rawToken;
        emailSender.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetLink);
        log.info("[PasswordResetService] Reset token issued for userId=[{}]", user.getId());
    }

    // -------------------------------------------------------
    // Consume a token and set the new password
    // -------------------------------------------------------

    @Transactional
    public void resetPassword(String rawToken, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new InvalidPasswordResetTokenException(
                    msg("auth.reset.password.mismatch"), "auth.reset.password.mismatch");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .filter(t -> t.isUsable(LocalDateTime.now()))
                .orElseThrow(() -> new InvalidPasswordResetTokenException(
                        msg("auth.reset.token.invalid"), "auth.reset.token.invalid"));

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // A password change must invalidate every outstanding session — otherwise a stolen
        // token would survive the reset the user just performed to lock the attacker out.
        jwtTokenRepository.revokeAllUserTokens(user.getId());
        log.info("[PasswordResetService] Password reset completed for userId=[{}]", user.getId());
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS — unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
