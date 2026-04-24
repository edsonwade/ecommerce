package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.CustomerRegistrationClient;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final CustomerRegistrationClient   customerRegistrationClient;

    public AuthService(UserRepository userRepository,
                       TokenRepository tokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authManager,
                       MessageSource messageSource,
                       RefreshTokenService refreshTokenService,
                       CustomerRegistrationClient customerRegistrationClient) {
        this.userRepository             = userRepository;
        this.tokenRepository            = tokenRepository;
        this.jwtService                 = jwtService;
        this.passwordEncoder            = passwordEncoder;
        this.authManager                = authManager;
        this.messageSource              = messageSource;
        this.refreshTokenService        = refreshTokenService;
        this.customerRegistrationClient = customerRegistrationClient;
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

        User user = User.builder()
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .tenantId(StringUtils.hasText(request.tenantId()) ? request.tenantId() : "default")
                .accountEnabled(true)
                .accountLocked(false)
                .build();

        User saved = userRepository.save(user);
        log.info(msg("auth.log.register.success", saved.getId(), saved.getEmail()));

        try {
            customerRegistrationClient.createCustomer(
                    new CustomerRegistrationClient.CustomerRegistrationRequest(
                            String.valueOf(saved.getId()),
                            saved.getFirstname(),
                            saved.getLastname(),
                            saved.getEmail()));
            log.info("Customer profile created for userId=[{}]", saved.getId());
        } catch (Exception ex) {
            log.warn("Failed to create customer profile for userId=[{}]: {}", saved.getId(), ex.getMessage());
        }

        String accessJwt  = jwtService.generateAccessToken(saved);
        String refreshJwt = jwtService.generateRefreshToken(saved);
        refreshTokenService.persistTokenPair(saved, accessJwt, refreshJwt);

        return AuthResponse.of(accessJwt, refreshJwt,
                String.valueOf(saved.getId()), saved.getEmail(),
                saved.getRole().name(), saved.getTenantId());
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
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException(
                    msg("auth.login.invalid.credentials"), "auth.login.invalid.credentials");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException(
                        msg("auth.login.invalid.credentials"), "auth.login.invalid.credentials"));

        tokenRepository.revokeAllUserTokens(user.getId());
        log.debug(msg("auth.token.revoke.all", user.getId()));

        String accessJwt  = jwtService.generateAccessToken(user);
        String refreshJwt = jwtService.generateRefreshToken(user);
        refreshTokenService.persistTokenPair(user, accessJwt, refreshJwt);

        log.info(msg("auth.log.login.success", user.getId(), user.getEmail()));
        return AuthResponse.of(accessJwt, refreshJwt,
                String.valueOf(user.getId()), user.getEmail(),
                user.getRole().name(), user.getTenantId());
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
