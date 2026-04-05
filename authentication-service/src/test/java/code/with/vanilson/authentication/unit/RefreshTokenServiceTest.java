package code.with.vanilson.authentication.unit;

import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.RefreshTokenService;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.Token;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.TokenExpiredException;
import code.with.vanilson.authentication.exception.TokenRevokedException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefreshTokenServiceTest — unit tests for token rotation logic.
 * Validates: type enforcement, DB revocation check, expiry, and user lookup.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {

    @Mock TokenRepository tokenRepository;
    @Mock UserRepository  userRepository;
    @Mock JwtService      jwtService;
    @Mock MessageSource   messageSource;

    @InjectMocks RefreshTokenService refreshTokenService;

    private User testUser;
    private Token validRefreshToken;

    private static final String REFRESH_JWT = "header.payload.signature";
    private static final String JTI         = "aaaabbbb-1111-2222-3333-444455556666";
    private static final String NEW_ACCESS   = "new.access.jwt";
    private static final String NEW_REFRESH  = "new.refresh.jwt";
    private static final String NEW_JTI_A    = "ccccdddd-5555-6666-7777-888899990000";
    private static final String NEW_JTI_R    = "eeeeffff-aaaa-bbbb-cccc-ddddeeee1111";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(10L)
                .email("user@example.com")
                .password("encoded")
                .role(Role.USER)
                .tenantId("default")
                .accountEnabled(true)
                .accountLocked(false)
                .build();

        validRefreshToken = Token.builder()
                .id(1L)
                .jti(JTI)
                .tokenType(Token.TokenType.REFRESH)
                .expired(false)
                .revoked(false)
                .user(testUser)
                .build();

        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("error message");
    }

    // -------------------------------------------------------
    // rotate() — happy path
    // -------------------------------------------------------
    @Nested
    @DisplayName("rotate() — happy path")
    class RotateHappyPath {

        @Test
        @DisplayName("valid refresh token returns new AuthResponse with new token pair")
        void validRefreshTokenRotatesSuccessfully() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn(JTI);
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("REFRESH");
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("user@example.com");
            when(tokenRepository.findValidTokenByJtiAndType(JTI, Token.TokenType.REFRESH))
                    .thenReturn(Optional.of(validRefreshToken));
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid(eq(REFRESH_JWT), any())).thenReturn(true);
            doNothing().when(tokenRepository).revokeAllUserTokens(anyLong());
            when(jwtService.generateAccessToken(testUser)).thenReturn(NEW_ACCESS);
            when(jwtService.generateRefreshToken(testUser)).thenReturn(NEW_REFRESH);
            when(jwtService.extractJti(NEW_ACCESS)).thenReturn(NEW_JTI_A);
            when(jwtService.extractJti(NEW_REFRESH)).thenReturn(NEW_JTI_R);
            when(tokenRepository.save(any())).thenReturn(null);

            AuthResponse resp = refreshTokenService.rotate(REFRESH_JWT);

            assertThat(resp.accessToken()).isEqualTo(NEW_ACCESS);
            assertThat(resp.refreshToken()).isEqualTo(NEW_REFRESH);
            assertThat(resp.email()).isEqualTo("user@example.com");
            assertThat(resp.role()).isEqualTo("USER");
            assertThat(resp.tokenType()).isEqualTo("Bearer");

            verify(tokenRepository).revokeAllUserTokens(testUser.getId());
        }

        @Test
        @DisplayName("rotation persists two new tokens (BEARER + REFRESH)")
        void rotationPersistsBothTokenTypes() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn(JTI);
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("REFRESH");
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("user@example.com");
            when(tokenRepository.findValidTokenByJtiAndType(JTI, Token.TokenType.REFRESH))
                    .thenReturn(Optional.of(validRefreshToken));
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid(eq(REFRESH_JWT), any())).thenReturn(true);
            doNothing().when(tokenRepository).revokeAllUserTokens(anyLong());
            when(jwtService.generateAccessToken(testUser)).thenReturn(NEW_ACCESS);
            when(jwtService.generateRefreshToken(testUser)).thenReturn(NEW_REFRESH);
            when(jwtService.extractJti(NEW_ACCESS)).thenReturn(NEW_JTI_A);
            when(jwtService.extractJti(NEW_REFRESH)).thenReturn(NEW_JTI_R);
            when(tokenRepository.save(any())).thenReturn(null);

            refreshTokenService.rotate(REFRESH_JWT);

            // persistTokenPair calls saveToken twice → tokenRepository.save twice
            verify(tokenRepository, org.mockito.Mockito.times(2)).save(any(Token.class));
        }
    }

    // -------------------------------------------------------
    // rotate() — type enforcement
    // -------------------------------------------------------
    @Nested
    @DisplayName("rotate() — token type enforcement")
    class TypeEnforcement {

        @Test
        @DisplayName("access token used as refresh token throws InvalidTokenException (HTTP 401)")
        void accessTokenAsRefreshThrows() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn(JTI);
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("ACCESS"); // wrong type
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("user@example.com");

            assertThatThrownBy(() -> refreshTokenService.rotate(REFRESH_JWT))
                    .isInstanceOf(InvalidTokenException.class)
                    .satisfies(ex ->
                        assertThat(((InvalidTokenException) ex).getHttpStatus().value()).isEqualTo(401));

            verify(tokenRepository, never()).findValidTokenByJtiAndType(anyString(), any());
        }
    }

    // -------------------------------------------------------
    // rotate() — DB revocation check
    // -------------------------------------------------------
    @Nested
    @DisplayName("rotate() — DB revocation check")
    class DbRevocationCheck {

        @Test
        @DisplayName("revoked refresh token throws TokenRevokedException (HTTP 401)")
        void revokedRefreshTokenThrows() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn(JTI);
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("REFRESH");
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("user@example.com");
            when(tokenRepository.findValidTokenByJtiAndType(JTI, Token.TokenType.REFRESH))
                    .thenReturn(Optional.empty()); // not found → revoked/expired

            assertThatThrownBy(() -> refreshTokenService.rotate(REFRESH_JWT))
                    .isInstanceOf(TokenRevokedException.class)
                    .satisfies(ex ->
                        assertThat(((TokenRevokedException) ex).getHttpStatus().value()).isEqualTo(401));
        }

        @Test
        @DisplayName("token not in DB at all throws TokenRevokedException")
        void tokenNotInDbThrows() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn("unknown-jti");
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("REFRESH");
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("user@example.com");
            when(tokenRepository.findValidTokenByJtiAndType("unknown-jti", Token.TokenType.REFRESH))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotate(REFRESH_JWT))
                    .isInstanceOf(TokenRevokedException.class);
        }
    }

    // -------------------------------------------------------
    // rotate() — expiry check
    // -------------------------------------------------------
    @Nested
    @DisplayName("rotate() — expiry check")
    class ExpiryCheck {

        @Test
        @DisplayName("cryptographically expired token throws TokenExpiredException (HTTP 401)")
        void expiredTokenThrows() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn(JTI);
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("REFRESH");
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("user@example.com");
            when(tokenRepository.findValidTokenByJtiAndType(JTI, Token.TokenType.REFRESH))
                    .thenReturn(Optional.of(validRefreshToken));
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
            when(jwtService.isTokenValid(eq(REFRESH_JWT), any())).thenReturn(false); // expired

            assertThatThrownBy(() -> refreshTokenService.rotate(REFRESH_JWT))
                    .isInstanceOf(TokenExpiredException.class)
                    .satisfies(ex ->
                        assertThat(((TokenExpiredException) ex).getHttpStatus().value()).isEqualTo(401));

            verify(tokenRepository, never()).revokeAllUserTokens(anyLong());
        }
    }

    // -------------------------------------------------------
    // rotate() — user lookup failure
    // -------------------------------------------------------
    @Nested
    @DisplayName("rotate() — user lookup failure")
    class UserLookupFailure {

        @Test
        @DisplayName("unknown user after DB token check throws InvalidCredentialsException (HTTP 401)")
        void unknownUserThrows() {
            when(jwtService.extractJti(REFRESH_JWT)).thenReturn(JTI);
            when(jwtService.extractTokenType(REFRESH_JWT)).thenReturn("REFRESH");
            when(jwtService.extractSubject(REFRESH_JWT)).thenReturn("ghost@example.com");
            when(tokenRepository.findValidTokenByJtiAndType(JTI, Token.TokenType.REFRESH))
                    .thenReturn(Optional.of(validRefreshToken));
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotate(REFRESH_JWT))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .satisfies(ex ->
                        assertThat(((InvalidCredentialsException) ex).getHttpStatus().value()).isEqualTo(401));
        }
    }

    // -------------------------------------------------------
    // persistTokenPair()
    // -------------------------------------------------------
    @Nested
    @DisplayName("persistTokenPair()")
    class PersistTokenPair {

        @Test
        @DisplayName("saves BEARER and REFRESH tokens with extracted JTIs")
        void savesBothTokenTypes() {
            when(jwtService.extractJti("access.jwt")).thenReturn("jti-access");
            when(jwtService.extractJti("refresh.jwt")).thenReturn("jti-refresh");
            when(tokenRepository.save(any())).thenReturn(null);

            refreshTokenService.persistTokenPair(testUser, "access.jwt", "refresh.jwt");

            verify(tokenRepository, org.mockito.Mockito.times(2)).save(any(Token.class));
        }
    }
}
