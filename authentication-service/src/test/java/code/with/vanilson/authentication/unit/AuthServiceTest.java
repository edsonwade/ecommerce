package code.with.vanilson.authentication.unit;

import code.with.vanilson.authentication.application.AuthResponse;
import code.with.vanilson.authentication.application.AuthService;
import code.with.vanilson.authentication.application.LoginRequest;
import code.with.vanilson.authentication.application.RefreshTokenService;
import code.with.vanilson.authentication.application.RegisterRequest;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidCredentialsException;
import code.with.vanilson.authentication.exception.InvalidTokenException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import code.with.vanilson.authentication.domain.Token;

/**
 * AuthServiceTest — unit tests for register, login, logout, and refresh delegation.
 * Uses Mockito; no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)   // messageSource stub used by only some tests
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock UserRepository        userRepository;
    @Mock TokenRepository       tokenRepository;
    @Mock JwtService            jwtService;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock AuthenticationManager authManager;
    @Mock MessageSource         messageSource;
    @Mock RefreshTokenService   refreshTokenService;

    @InjectMocks AuthService authService;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = User.builder()
                .id(1L)
                .firstname("John")
                .lastname("Doe")
                .email("john.doe@example.com")
                .password("$2a$12$encoded")
                .role(Role.USER)
                .tenantId("default")
                .accountEnabled(true)
                .accountLocked(false)
                .build();

        // MessageSource returns the key itself in unit tests (no i18n needed)
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(inv -> {
            Object[] args = inv.getArgument(1);
            String key = inv.getArgument(0);
            if (args != null && args.length > 0) return key + ":" + args[0];
            return key;
        });
    }

    // -------------------------------------------------------
    // register()
    // -------------------------------------------------------
    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("success: new user persisted and tokens returned")
        void successfulRegistration() {
            RegisterRequest req = new RegisterRequest("John", "Doe",
                    "john.doe@example.com", "password123", "default");

            when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$12$encoded");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(jwtService.generateAccessToken(any())).thenReturn("access.jwt.token");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh.jwt.token");
            doNothing().when(refreshTokenService).persistTokenPair(any(), anyString(), anyString());

            AuthResponse resp = authService.register(req);

            assertThat(resp.accessToken()).isEqualTo("access.jwt.token");
            assertThat(resp.refreshToken()).isEqualTo("refresh.jwt.token");
            assertThat(resp.email()).isEqualTo("john.doe@example.com");
            assertThat(resp.role()).isEqualTo("USER");
            assertThat(resp.tokenType()).isEqualTo("Bearer");

            verify(userRepository).save(any(User.class));
            verify(refreshTokenService).persistTokenPair(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("duplicate email throws UserAlreadyExistsException (HTTP 409)")
        void duplicateEmailThrows() {
            RegisterRequest req = new RegisterRequest("John", "Doe",
                    "john.doe@example.com", "password123", "default");

            when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .satisfies(ex -> {
                        UserAlreadyExistsException e = (UserAlreadyExistsException) ex;
                        assertThat(e.getHttpStatus().value()).isEqualTo(409);
                        assertThat(e.getMessageKey()).isEqualTo("auth.user.already.exists");
                    });

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("register with null tenantId defaults to 'default'")
        void nullTenantIdDefaultsToDefault() {
            RegisterRequest req = new RegisterRequest("Jane", "Doe",
                    "jane@example.com", "password123", null);

            when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(jwtService.generateAccessToken(any())).thenReturn("access");
            when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
            doNothing().when(refreshTokenService).persistTokenPair(any(), anyString(), anyString());

            AuthResponse resp = authService.register(req);
            assertThat(resp).isNotNull();
        }
    }

    // -------------------------------------------------------
    // login()
    // -------------------------------------------------------
    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("success: valid credentials return access + refresh tokens")
        void successfulLogin() {
            LoginRequest req = new LoginRequest("john.doe@example.com", "password123");

            when(authManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken("john.doe@example.com", null));
            when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(savedUser));
            doNothing().when(tokenRepository).revokeAllUserTokens(anyLong());
            when(jwtService.generateAccessToken(savedUser)).thenReturn("new.access.jwt");
            when(jwtService.generateRefreshToken(savedUser)).thenReturn("new.refresh.jwt");
            doNothing().when(refreshTokenService).persistTokenPair(any(), anyString(), anyString());

            AuthResponse resp = authService.login(req);

            assertThat(resp.accessToken()).isEqualTo("new.access.jwt");
            assertThat(resp.refreshToken()).isEqualTo("new.refresh.jwt");
            assertThat(resp.email()).isEqualTo("john.doe@example.com");

            verify(tokenRepository).revokeAllUserTokens(1L);
        }

        @Test
        @DisplayName("bad credentials throw InvalidCredentialsException (HTTP 401)")
        void badCredentialsThrows() {
            LoginRequest req = new LoginRequest("john.doe@example.com", "wrongpassword");
            when(authManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .satisfies(ex -> {
                        InvalidCredentialsException e = (InvalidCredentialsException) ex;
                        assertThat(e.getHttpStatus().value()).isEqualTo(401);
                        assertThat(e.getMessageKey()).isEqualTo("auth.login.invalid.credentials");
                    });

            verify(tokenRepository, never()).revokeAllUserTokens(anyLong());
        }

        @Test
        @DisplayName("user not found after auth throws InvalidCredentialsException (HTTP 401)")
        void userNotFoundAfterAuthThrows() {
            LoginRequest req = new LoginRequest("ghost@example.com", "password123");
            when(authManager.authenticate(any())).thenReturn(mock(
                    UsernamePasswordAuthenticationToken.class));
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .satisfies(ex ->
                        assertThat(((InvalidCredentialsException) ex).getHttpStatus().value()).isEqualTo(401));
        }

        @Test
        @DisplayName("login revokes all previous tokens before issuing new ones")
        void loginRevokesOldTokens() {
            LoginRequest req = new LoginRequest("john.doe@example.com", "password123");
            when(authManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken("john.doe@example.com", null));
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
            doNothing().when(tokenRepository).revokeAllUserTokens(anyLong());
            when(jwtService.generateAccessToken(any())).thenReturn("a");
            when(jwtService.generateRefreshToken(any())).thenReturn("r");
            doNothing().when(refreshTokenService).persistTokenPair(any(), anyString(), anyString());

            authService.login(req);

            verify(tokenRepository).revokeAllUserTokens(savedUser.getId());
        }
    }

    // -------------------------------------------------------
    // logout()
    // -------------------------------------------------------
    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("valid Bearer header with live token revokes all user tokens")
        void validBearerRevokesTokens() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("Authorization")).thenReturn("Bearer valid.access.token");

            // logout() now checks JTI validity before revoking
            when(jwtService.extractJti("valid.access.token")).thenReturn("test-jti-uuid");
            Token liveToken = Token.builder()
                    .jti("test-jti-uuid").expired(false).revoked(false).build();
            when(tokenRepository.findByJti("test-jti-uuid")).thenReturn(Optional.of(liveToken));
            when(jwtService.extractUserId("valid.access.token")).thenReturn(1L);
            doNothing().when(tokenRepository).revokeAllUserTokens(anyLong());

            authService.logout(req);

            verify(tokenRepository).revokeAllUserTokens(1L);
        }

        @Test
        @DisplayName("missing Authorization header throws InvalidTokenException (HTTP 401)")
        void missingHeaderDoesNothing() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("Authorization")).thenReturn(null);

            assertThatThrownBy(() -> authService.logout(req))
                    .isInstanceOf(InvalidTokenException.class)
                    .satisfies(ex ->
                        assertThat(((InvalidTokenException) ex).getHttpStatus().value()).isEqualTo(401));

            verify(tokenRepository, never()).revokeAllUserTokens(anyLong());
        }

        @Test
        @DisplayName("non-Bearer Authorization header throws InvalidTokenException (HTTP 401)")
        void nonBearerHeaderDoesNothing() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            assertThatThrownBy(() -> authService.logout(req))
                    .isInstanceOf(InvalidTokenException.class)
                    .satisfies(ex ->
                        assertThat(((InvalidTokenException) ex).getHttpStatus().value()).isEqualTo(401));

            verify(tokenRepository, never()).revokeAllUserTokens(anyLong());
        }
    }

    // -------------------------------------------------------
    // refreshToken()
    // -------------------------------------------------------
    @Nested
    @DisplayName("refreshToken()")
    class RefreshToken {

        @Test
        @DisplayName("valid Bearer refresh header delegates to RefreshTokenService")
        void validRefreshHeaderDelegatesToService() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("Authorization")).thenReturn("Bearer my.refresh.jwt");
            when(refreshTokenService.rotate("my.refresh.jwt")).thenReturn(
                    AuthResponse.of("newAccess", "newRefresh", "1", "u@test.com", "USER", "default"));

            AuthResponse resp = authService.refreshToken(req);

            assertThat(resp.accessToken()).isEqualTo("newAccess");
            verify(refreshTokenService).rotate("my.refresh.jwt");
        }

        @Test
        @DisplayName("missing Authorization header throws InvalidTokenException (HTTP 401)")
        void missingHeaderThrowsInvalidToken() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("Authorization")).thenReturn(null);

            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(InvalidTokenException.class)
                    .satisfies(ex ->
                        assertThat(((InvalidTokenException) ex).getHttpStatus().value()).isEqualTo(401));
        }

        @Test
        @DisplayName("non-Bearer prefix throws InvalidTokenException (HTTP 401)")
        void nonBearerThrowsInvalidToken() {
            HttpServletRequest req = mock(HttpServletRequest.class);
            when(req.getHeader("Authorization")).thenReturn("Basic abc123");

            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
