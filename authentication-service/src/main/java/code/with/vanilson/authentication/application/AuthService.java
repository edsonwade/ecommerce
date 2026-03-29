package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.Token;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.TokenRevokedException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
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
 * Orchestrates registration, login, logout, and token refresh.
 * <p>
 * Key design decisions:
 * 1. On login: revoke all previous tokens, issue fresh access + refresh pair.
 *    Prevents token accumulation and ensures logout-from-all-devices works.
 * 2. On refresh: validate refresh token in DB (not revoked), generate new access token.
 *    Refresh token itself is re-issued (rotation) — stolen refresh tokens become one-time use.
 * 3. On logout: revoke the presented token only.
 * 4. Password encoding via PasswordEncoder injected from SecurityConfig (DIP — SOLID-D).
 * 5. All messages from messages.properties (SRP — SOLID-S).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
public class AuthService {

    private final UserRepository      userRepository;
    private final TokenRepository     tokenRepository;
    private final JwtService          jwtService;
    private final PasswordEncoder     passwordEncoder;
    private final AuthenticationManager authManager;
    private final MessageSource       messageSource;

    public AuthService(UserRepository userRepository, TokenRepository tokenRepository,
                       JwtService jwtService, PasswordEncoder passwordEncoder,
                       AuthenticationManager authManager, MessageSource messageSource) {
        this.userRepository   = userRepository;
        this.tokenRepository  = tokenRepository;
        this.jwtService       = jwtService;
        this.passwordEncoder  = passwordEncoder;
        this.authManager      = authManager;
        this.messageSource    = messageSource;
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

        String accessToken  = jwtService.generateAccessToken(saved);
        String refreshToken = jwtService.generateRefreshToken(saved);
        saveToken(saved, accessToken, Token.TokenType.BEARER);

        return AuthResponse.of(accessToken, refreshToken,
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

        // Revoke all existing tokens — single active session per user
        tokenRepository.revokeAllUserTokens(user.getId());
        log.debug(msg("auth.token.revoke.all", user.getId()));

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        saveToken(user, accessToken, Token.TokenType.BEARER);

        log.info(msg("auth.log.login.success", user.getId(), user.getEmail()));
        return AuthResponse.of(accessToken, refreshToken,
                String.valueOf(user.getId()), user.getEmail(),
                user.getRole().name(), user.getTenantId());
    }

    // -------------------------------------------------------
    // Refresh Token
    // -------------------------------------------------------

    @Transactional
    public AuthResponse refreshToken(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new TokenRevokedException(
                    msg("auth.token.revoked"), "auth.token.revoked");
        }

        String refreshToken = authHeader.substring(7);
        String userEmail    = jwtService.extractSubject(refreshToken);

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new TokenRevokedException(
                        msg("auth.token.revoked"), "auth.token.revoked"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new TokenRevokedException(msg("auth.jwt.expired"), "auth.jwt.expired");
        }

        log.info(msg("auth.log.token.refresh", user.getId()));

        tokenRepository.revokeAllUserTokens(user.getId());
        String newAccessToken  = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        saveToken(user, newAccessToken, Token.TokenType.BEARER);

        return AuthResponse.of(newAccessToken, newRefreshToken,
                String.valueOf(user.getId()), user.getEmail(),
                user.getRole().name(), user.getTenantId());
    }

    // -------------------------------------------------------
    // Logout
    // -------------------------------------------------------

    @Transactional
    public void logout(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return; // already logged out or no token presented
        }

        String tokenValue = authHeader.substring(7);
        log.info(msg("auth.log.logout.attempt", jwtService.extractUserId(tokenValue)));

        tokenRepository.findByTokenValue(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            token.setExpired(true);
            tokenRepository.save(token);
        });
    }

    // -------------------------------------------------------
    private void saveToken(User user, String tokenValue, Token.TokenType type) {
        Token token = Token.builder()
                .tokenValue(tokenValue)
                .tokenType(type)
                .user(user)
                .expired(false)
                .revoked(false)
                .build();
        tokenRepository.save(token);
        log.debug(msg("auth.token.saved", user.getId(), type));
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
