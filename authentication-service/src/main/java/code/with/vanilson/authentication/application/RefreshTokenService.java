package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Token;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.TokenExpiredException;
import code.with.vanilson.authentication.exception.TokenRevokedException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RefreshTokenService — Application Layer
 * <p>
 * Single Responsibility: owns the refresh token lifecycle:
 *   - Validate a presented refresh JWT against DB + cryptographic checks
 *   - Rotate: revoke old pair, issue new pair, persist new pair
 * <p>
 * AuthService delegates the /refresh endpoint flow here.
 * This keeps AuthService focused on user identity flows (register/login/logout).
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final TokenRepository tokenRepository;
    private final UserRepository  userRepository;
    private final JwtService      jwtService;
    private final MessageSource   messageSource;

    /**
     * Validates the refresh JWT, rotates the token pair, and returns a new AuthResponse.
     *
     * Validation order (order is critical):
     *   1. Extract JTI + tokenType claim
     *   2. Reject if tokenType != "REFRESH"    ← prevents access token misuse
     *   3. DB lookup by JTI + type             ← catches revoked tokens (logout bypass fix)
     *   4. Cryptographic + expiry check         ← verifies signature + not expired
     *   5. Rotate: revoke old pair, issue new pair
     */
    @Transactional
    public AuthResponse rotate(String refreshJwt) {
        String jti       = jwtService.extractJti(refreshJwt);
        String tokenType = jwtService.extractTokenType(refreshJwt);
        String userEmail = jwtService.extractSubject(refreshJwt);

        if (!"REFRESH".equals(tokenType)) {
            throw new InvalidTokenException(
                    msg("auth.jwt.invalid"), "auth.jwt.invalid");
        }

        tokenRepository.findValidTokenByJtiAndType(jti, Token.TokenType.REFRESH)
                .orElseThrow(() -> new TokenRevokedException(
                        msg("auth.token.refresh.invalid"), "auth.token.refresh.invalid"));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new InvalidCredentialsException(
                        msg("auth.login.invalid.credentials"), "auth.login.invalid.credentials"));

        UserDetails adapter = new UserDetailsAdapter(user);
        if (!jwtService.isTokenValid(refreshJwt, adapter)) {
            throw new TokenExpiredException(msg("auth.jwt.expired"), "auth.jwt.expired");
        }

        log.info(msg("auth.log.token.refresh", user.getId()));

        tokenRepository.revokeAllUserTokens(user.getId());
        String newAccessJwt  = jwtService.generateAccessToken(user);
        String newRefreshJwt = jwtService.generateRefreshToken(user);
        persistTokenPair(user, newAccessJwt, newRefreshJwt);

        return AuthResponse.of(newAccessJwt, newRefreshJwt,
                String.valueOf(user.getId()), user.getEmail(),
                user.getRole().name(), user.getTenantId());
    }

    /**
     * Persists an access + refresh token pair as two DB rows (JTI only — never the full JWT).
     * Called by AuthService after register and login.
     */
    public void persistTokenPair(User user, String accessJwt, String refreshJwt) {
        saveToken(user, jwtService.extractJti(accessJwt),  Token.TokenType.BEARER);
        saveToken(user, jwtService.extractJti(refreshJwt), Token.TokenType.REFRESH);
    }

    private void saveToken(User user, String jti, Token.TokenType type) {
        tokenRepository.save(Token.builder()
                .jti(jti)
                .tokenType(type)
                .user(user)
                .expired(false)
                .revoked(false)
                .build());
        log.debug(msg("auth.token.saved", user.getId(), type));
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
