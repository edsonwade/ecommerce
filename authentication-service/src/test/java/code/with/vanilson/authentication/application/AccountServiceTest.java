package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.SellerStatus;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.InvalidAccountPasswordException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock UserRepository       userRepository;
    @Mock TokenRepository      tokenRepository;
    @Mock JwtService           jwtService;
    @Mock PasswordEncoder      passwordEncoder;
    @Mock RefreshTokenService  refreshTokenService;
    @Mock CustomerProvisioning customerProvisioning;
    @Mock MessageSource        messageSource;

    @InjectMocks AccountService service;

    private User user;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any()))
                 .thenAnswer(inv -> inv.getArgument(0)); // key echoes back as message
        user = User.builder()
                .id(7L).firstname("Ana").lastname("Silva")
                .email("ana@x.com").password("$2a$10$hash")
                .role(Role.USER).tenantId("default")
                .accountEnabled(true).accountLocked(false)
                .build();
    }

    @Nested @DisplayName("GetAccount")
    class GetAccount {
        @Test
        void returns_account_view_of_the_user() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            AccountResponse res = service.getAccount(7L);
            assertThat(res.id()).isEqualTo(7L);
            assertThat(res.email()).isEqualTo("ana@x.com");
            assertThat(res.role()).isEqualTo("USER");
        }

        @Test
        @DisplayName("leaves sellerStatus null for a non-seller account")
        void non_seller_has_null_seller_status() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            assertThat(service.getAccount(7L).sellerStatus()).isNull();
        }

        @Test
        @DisplayName("exposes the live sellerStatus for a seller (drives the SPA poll → no re-login)")
        void seller_status_is_mapped_from_the_entity() {
            User seller = User.builder()
                    .id(8L).firstname("Sam").lastname("Seller")
                    .email("seller@x.com").password("$2a$10$hash")
                    .role(Role.SELLER).tenantId("default")
                    .sellerStatus(SellerStatus.PENDING_APPROVAL)
                    .accountEnabled(true).accountLocked(false)
                    .build();
            when(userRepository.findById(8L)).thenReturn(Optional.of(seller));
            assertThat(service.getAccount(8L).sellerStatus()).isEqualTo("PENDING_APPROVAL");
        }
    }

    @Nested @DisplayName("UpdateAccount")
    class UpdateAccount {
        @Test
        void name_only_change_saves_without_new_tokens_and_syncs_profile() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AccountUpdateResponse res = service.updateAccount(7L,
                    new UpdateAccountRequest("Ana Maria", "Silva", "ana@x.com", null));

            assertThat(res.tokens()).isNull();
            assertThat(res.account().firstname()).isEqualTo("Ana Maria");
            verify(customerProvisioning).syncCustomerProfile(any(User.class));
            verify(tokenRepository, never()).revokeAllUserTokens(any());
        }

        @Test
        void email_change_with_valid_password_rotates_tokens() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$10$hash")).thenReturn(true);
            when(userRepository.existsByEmail("new@x.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access");
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh");

            AccountUpdateResponse res = service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "new@x.com", "Secret123!"));

            assertThat(res.tokens()).isNotNull();
            assertThat(res.tokens().accessToken()).isEqualTo("access");
            assertThat(res.account().email()).isEqualTo("new@x.com");
            verify(tokenRepository).revokeAllUserTokens(7L);
            verify(refreshTokenService).persistTokenPair(any(User.class), eq("access"), eq("refresh"));
            verify(customerProvisioning).syncCustomerProfile(any(User.class));
        }

        @Test
        void email_change_with_wrong_password_is_rejected() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "new@x.com", "wrong")))
                    .isInstanceOf(InvalidAccountPasswordException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void email_change_without_password_is_rejected() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "new@x.com", null)))
                    .isInstanceOf(InvalidAccountPasswordException.class);
        }

        @Test
        void email_change_to_taken_address_is_409() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$10$hash")).thenReturn(true);
            when(userRepository.existsByEmail("taken@x.com")).thenReturn(true);

            assertThatThrownBy(() -> service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "taken@x.com", "Secret123!")))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void same_email_different_case_is_not_an_email_change() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            AccountUpdateResponse res = service.updateAccount(7L,
                    new UpdateAccountRequest("Ana", "Silva", "ANA@X.COM", null));

            assertThat(res.tokens()).isNull();
            verify(tokenRepository, never()).revokeAllUserTokens(any());
        }
    }

    @Nested @DisplayName("ChangePassword")
    class ChangePassword {
        @Test
        void valid_change_rehashes_revokes_sessions_and_returns_fresh_pair() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass123!", "$2a$10$hash")).thenReturn(true);
            when(passwordEncoder.encode("NewPass123!")).thenReturn("$2a$10$newhash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access2");
            when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh2");

            AuthResponse res = service.changePassword(7L,
                    new ChangePasswordRequest("OldPass123!", "NewPass123!", "NewPass123!"));

            assertThat(res.accessToken()).isEqualTo("access2");
            assertThat(user.getPassword()).isEqualTo("$2a$10$newhash");
            verify(tokenRepository).revokeAllUserTokens(7L);
            verify(refreshTokenService).persistTokenPair(any(User.class), eq("access2"), eq("refresh2"));
        }

        @Test
        void wrong_current_password_is_rejected() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.changePassword(7L,
                    new ChangePasswordRequest("wrong", "NewPass123!", "NewPass123!")))
                    .isInstanceOf(InvalidAccountPasswordException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void mismatched_confirmation_is_rejected_without_touching_the_user() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("OldPass123!", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> service.changePassword(7L,
                    new ChangePasswordRequest("OldPass123!", "NewPass123!", "Different!")))
                    .isInstanceOf(InvalidAccountPasswordException.class)
                    .hasMessageContaining("auth.account.password.mismatch");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("DeleteAccount")
    class DeleteAccount {
        @Test
        void soft_deletes_anonymizes_revokes_and_propagates() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Secret123!", "$2a$10$hash")).thenReturn(true);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            service.deleteAccount(7L, "Secret123!");

            assertThat(user.isAccountEnabled()).isFalse();
            assertThat(user.getEmail()).isEqualTo("deleted-7@removed.local");
            assertThat(user.getFirstname()).isEqualTo("Deleted");
            assertThat(user.getLastname()).isEqualTo("User");
            verify(tokenRepository).revokeAllUserTokens(7L);
            verify(customerProvisioning).deleteCustomerProfile(7L);
        }

        @Test
        void wrong_password_blocks_deletion() {
            when(userRepository.findById(7L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteAccount(7L, "wrong"))
                    .isInstanceOf(InvalidAccountPasswordException.class);
            assertThat(user.isAccountEnabled()).isTrue();
            verify(userRepository, never()).save(any());
        }
    }
}
