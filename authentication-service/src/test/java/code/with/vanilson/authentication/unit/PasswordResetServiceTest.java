package code.with.vanilson.authentication.unit;

import code.with.vanilson.authentication.application.PasswordResetService;
import code.with.vanilson.authentication.domain.PasswordResetToken;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidPasswordResetTokenException;
import code.with.vanilson.authentication.infrastructure.PasswordResetTokenRepository;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import code.with.vanilson.authentication.infrastructure.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PasswordResetServiceTest — pure Mockito unit test for the forgot-password flow.
 * No Spring context: the @Value fields are injected via ReflectionTestUtils.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PasswordResetService Unit Tests")
class PasswordResetServiceTest {

    @Mock UserRepository               userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock TokenRepository              jwtTokenRepository;
    @Mock PasswordEncoder              passwordEncoder;
    @Mock EmailSender                  emailSender;
    @Mock MessageSource                messageSource;

    private PasswordResetService service;

    private static final String RESET_URL_BASE = "http://localhost:8080/reset-password";

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, jwtTokenRepository,
                passwordEncoder, emailSender, messageSource);
        ReflectionTestUtils.setField(service, "tokenTtl", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(service, "resetUrlBase", RESET_URL_BASE);
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("error message");
    }

    private User aUser() {
        return User.builder()
                .id(7L).firstname("Ada").lastname("Lovelace")
                .email("ada@example.com").password("$2a$10$oldhash")
                .role(Role.USER).tenantId("default").accountEnabled(true).build();
    }

    private static String sha256Hex(String raw) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(UTF_8)));
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("requestReset")
    class RequestReset {

        @Test
        @DisplayName("issues a hashed single-use token and emails the link for an active user")
        void issuesTokenAndEmailsLink() throws Exception {
            User user = aUser();
            when(userRepository.findActiveUserByEmail("ada@example.com")).thenReturn(Optional.of(user));

            service.requestReset("ada@example.com");

            // Prior live tokens are burned first
            verify(tokenRepository).invalidateActiveTokensForUser(eq(7L), any(LocalDateTime.class));

            // A token row is persisted — capture it to inspect the stored hash
            ArgumentCaptor<PasswordResetToken> tokenCaptor =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(tokenRepository).save(tokenCaptor.capture());
            PasswordResetToken saved = tokenCaptor.getValue();
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getTokenHash()).hasSize(64);          // hex SHA-256
            assertThat(saved.getUsedAt()).isNull();
            assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());

            // The email carries the RAW token; its SHA-256 must equal the stored hash
            ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailSender).sendPasswordResetEmail(eq("ada@example.com"), eq("Ada Lovelace"),
                    linkCaptor.capture());
            String link = linkCaptor.getValue();
            assertThat(link).startsWith(RESET_URL_BASE + "?token=");
            String rawToken = link.substring((RESET_URL_BASE + "?token=").length());
            assertThat(sha256Hex(rawToken)).isEqualTo(saved.getTokenHash());
        }

        @Test
        @DisplayName("silently no-ops for an unknown email (no enumeration, no email)")
        void unknownEmailIsSilentNoOp() {
            when(userRepository.findActiveUserByEmail("ghost@example.com")).thenReturn(Optional.empty());

            service.requestReset("ghost@example.com");

            verify(tokenRepository, never()).save(any());
            verifyNoInteractions(emailSender);
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("resetPassword")
    class ResetPassword {

        @Test
        @DisplayName("valid token sets new password, consumes token, and revokes all sessions")
        void validTokenResetsPassword() throws Exception {
            User user = aUser();
            String raw = "valid-raw-token";
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(1L).user(user).tokenHash(sha256Hex(raw))
                    .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
            when(tokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));
            when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$10$newhash");

            service.resetPassword(raw, "NewPass123", "NewPass123");

            assertThat(user.getPassword()).isEqualTo("$2a$10$newhash");
            verify(userRepository).save(user);
            assertThat(token.getUsedAt()).isNotNull();
            verify(tokenRepository).save(token);
            verify(jwtTokenRepository).revokeAllUserTokens(7L);
        }

        @Test
        @DisplayName("mismatched passwords are rejected before any token lookup")
        void mismatchedPasswordsRejected() {
            assertThatThrownBy(() -> service.resetPassword("any", "NewPass123", "Different456"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);

            verify(tokenRepository, never()).findByTokenHash(anyString());
            verify(jwtTokenRepository, never()).revokeAllUserTokens(anyLong());
        }

        @Test
        @DisplayName("unknown token is rejected")
        void unknownTokenRejected() {
            when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword("ghost", "NewPass123", "NewPass123"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired token is rejected")
        void expiredTokenRejected() throws Exception {
            String raw = "expired-raw";
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(2L).user(aUser()).tokenHash(sha256Hex(raw))
                    .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
            when(tokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.resetPassword(raw, "NewPass123", "NewPass123"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("already-used token is rejected")
        void usedTokenRejected() throws Exception {
            String raw = "used-raw";
            PasswordResetToken token = PasswordResetToken.builder()
                    .id(3L).user(aUser()).tokenHash(sha256Hex(raw))
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .usedAt(LocalDateTime.now().minusMinutes(2)).build();
            when(tokenRepository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> service.resetPassword(raw, "NewPass123", "NewPass123"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);

            verify(jwtTokenRepository, never()).revokeAllUserTokens(anyLong());
        }
    }
}
