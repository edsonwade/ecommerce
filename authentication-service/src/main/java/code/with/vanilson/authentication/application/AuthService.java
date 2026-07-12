package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.SellerStatus;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.RegistrationException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * AuthService — Application Layer
 * <p>
 * Orchestrates registration, login, and logout.
 * Token refresh is delegated to {@link RefreshTokenService} (SRP).
 * Token persistence is delegated to {@link RefreshTokenService#persistTokenPair}.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository               userRepository;
    private final TokenRepository              tokenRepository;
    private final JwtService                   jwtService;
    private final PasswordEncoder              passwordEncoder;
    private final AuthenticationManager        authManager;
    private final MessageSource                messageSource;
    private final RefreshTokenService          refreshTokenService;
    private final CustomerProvisioning         customerProvisioning;

    // Must match SecurityConfig's BCrypt strength so login can detect a user whose stored
    // hash was computed at a different cost and transparently re-hash it (see login()).
    @Value("${security.bcrypt.strength:10}")
    private int bcryptStrength;

    public AuthService(UserRepository userRepository,
                       TokenRepository tokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authManager,
                       MessageSource messageSource,
                       RefreshTokenService refreshTokenService,
                       CustomerProvisioning customerProvisioning) {
        this.userRepository             = userRepository;
        this.tokenRepository            = tokenRepository;
        this.jwtService                 = jwtService;
        this.passwordEncoder            = passwordEncoder;
        this.authManager                = authManager;
        this.messageSource              = messageSource;
        this.refreshTokenService        = refreshTokenService;
        this.customerProvisioning       = customerProvisioning;
    }

    // -------------------------------------------------------
    // Register
    // -------------------------------------------------------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info(msg("auth.log.register.attempt", request.email()));

        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(
                    msg("auth.user.already.exists", request.email()),
                    "auth.user.already.exists");
        }

        Role userRole = Role.USER;
        if (StringUtils.hasText(request.role())) {
            try {
                userRole = Role.valueOf(request.role().toUpperCase());
                if (userRole == Role.ADMIN) {
                    throw new RegistrationException(
                            msg("auth.register.admin.denied"), "auth.register.admin.denied");
                }
            } catch (IllegalArgumentException e) {
                throw new RegistrationException(
                        msg("auth.register.invalid.role", request.role()), "auth.register.invalid.role");
            }
        }

        User user = User.builder()
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(userRole)
                .tenantId(StringUtils.hasText(request.tenantId()) ? request.tenantId() : "default")
                // Self-registered sellers await admin approval before they may write products;
                // admin-created sellers (UserManagementService.createUser) are born APPROVED.
                .sellerStatus(userRole == Role.SELLER ? SellerStatus.PENDING_APPROVAL : null)
                .accountEnabled(true)
                .accountLocked(false)
                .build();

        User saved = userRepository.save(user);
        log.info(msg("auth.log.register.success", saved.getId(), saved.getEmail()));

        // Fire-and-forget — creates the customer profile off the request thread so register()
        // returns the JWT immediately instead of blocking on customer-service.
        customerProvisioning.ensureCustomerProfile(saved);

        String accessJwt  = jwtService.generateAccessToken(saved);
        String refreshJwt = jwtService.generateRefreshToken(saved);
        refreshTokenService.persistTokenPair(saved, accessJwt, refreshJwt);

        return AuthResponse.of(accessJwt, refreshJwt,
                String.valueOf(saved.getId()), saved.getEmail(),
                saved.getRole().name(), saved.getTenantId(),
                saved.getSellerStatus() != null ? saved.getSellerStatus().name() : null);
    }

    // -------------------------------------------------------
    // Login
    // -------------------------------------------------------

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info(msg("auth.log.login.attempt", request.email()));

        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (DisabledException ex) {
            // Deactivated account — the pre-auth check throws before the password is verified.
            // This is an authentication failure (401), not an internal error (500).
            throw new InvalidCredentialsException(
                    msg("auth.user.disabled", request.email()), "auth.user.disabled");
        } catch (LockedException ex) {
            // Locked account — likewise a 401, not a 500.
            throw new InvalidCredentialsException(
                    msg("auth.user.locked", request.email()), "auth.user.locked");
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException(
                    msg("auth.login.invalid.credentials"), "auth.login.invalid.credentials");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException(
                        msg("auth.login.invalid.credentials"), "auth.login.invalid.credentials"));

        // Credentials are already proven valid above. If this user's hash was computed at a
        // different BCrypt cost than we now use (e.g. legacy cost-12 accounts after we lowered
        // the factor), re-hash with the raw password so their NEXT login is fast.
        rehashIfStrengthChanged(user, request.password());

        tokenRepository.revokeAllUserTokens(user.getId());
        log.debug(msg("auth.token.revoke.all", user.getId()));

        String accessJwt  = jwtService.generateAccessToken(user);
        String refreshJwt = jwtService.generateRefreshToken(user);
        refreshTokenService.persistTokenPair(user, accessJwt, refreshJwt);

        // Idempotent backfill for legacy users — runs off the request thread so login()
        // never waits on customer-service. Fail-open: login has already succeeded.
        customerProvisioning.ensureCustomerProfile(user);

        log.info(msg("auth.log.login.success", user.getId(), user.getEmail()));
        return AuthResponse.of(accessJwt, refreshJwt,
                String.valueOf(user.getId()), user.getEmail(),
                user.getRole().name(), user.getTenantId(),
                user.getSellerStatus() != null ? user.getSellerStatus().name() : null);
    }

    /**
     * Transparently re-hashes a user's password if its stored BCrypt cost differs from the
     * currently-configured strength. Spring's {@code upgradeEncoding} only migrates UPWARD, so
     * this handles the lower-the-cost case too. Called only AFTER credentials are verified, so
     * {@code rawPassword} is known-correct. Safe no-op for non-BCrypt or unparseable hashes, and
     * skipped entirely when strength is unset (plain unit tests with no Spring context).
     */
    private void rehashIfStrengthChanged(User user, String rawPassword) {
        if (bcryptStrength <= 0) {
            return;
        }
        String hash = user.getPassword();
        // BCrypt format: $2a$<cost>$<22-char salt><31-char hash> — cost is at index 4-5.
        if (hash == null || hash.length() < 7 || hash.charAt(0) != '$') {
            return;
        }
        try {
            int storedStrength = Integer.parseInt(hash.substring(4, 6));
            if (storedStrength != bcryptStrength) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                userRepository.save(user);
                log.info("[AuthService] Re-hashed password for userId=[{}] bcrypt strength {}->{}",
                        user.getId(), storedStrength, bcryptStrength);
            }
        } catch (NumberFormatException ignore) {
            // Hash prefix isn't a numeric cost — not standard BCrypt; leave it untouched.
        }
    }

    // -------------------------------------------------------
    // Refresh Token — delegated to RefreshTokenService
    // -------------------------------------------------------

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException(msg("auth.jwt.invalid"), "auth.jwt.invalid");
        }
        return refreshTokenService.rotate(authHeader.substring(7));
    }

    // -------------------------------------------------------
    // Logout — revoke ALL tokens (closes the refresh bypass)
    // -------------------------------------------------------

    @Transactional
    public void logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            // No token at all → 401, not silent 204
            throw new InvalidTokenException(msg("auth.jwt.invalid"), "auth.jwt.invalid");
        }
        String jwt = authHeader.substring(7);

        // Reject revoked / unknown tokens — prevents silent 204 on reuse after logout
        String  jti        = jwtService.extractJti(jwt);
        boolean tokenValid = tokenRepository.findByJti(jti)
                .map(t -> !t.isExpired() && !t.isRevoked())
                .orElse(false);
        if (!tokenValid) {
            throw new InvalidTokenException(msg("auth.jwt.invalid"), "auth.jwt.invalid");
        }

        Long userId = jwtService.extractUserId(jwt);
        log.info(msg("auth.log.logout.attempt", userId));
        tokenRepository.revokeAllUserTokens(userId);
    }

    // -------------------------------------------------------
    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
