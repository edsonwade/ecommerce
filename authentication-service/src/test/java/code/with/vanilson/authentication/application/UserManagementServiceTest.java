package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.AuditLog;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AdminActionNotAllowedException;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.exception.UserAlreadyExistsException;
import code.with.vanilson.authentication.infrastructure.AuditLogRepository;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserManagementService — unit tests")
class UserManagementServiceTest {

    private final UserRepository       userRepo        = mock(UserRepository.class);
    private final AuditLogRepository   auditRepo       = mock(AuditLogRepository.class);
    private final PasswordEncoder      passwordEncoder = mock(PasswordEncoder.class);
    private final CustomerProvisioning provisioning    = mock(CustomerProvisioning.class);
    private final TokenRepository      tokenRepo       = mock(TokenRepository.class);
    private final AccountService       accountService  = mock(AccountService.class);
    private final MessageSource        messageSource   = mock(MessageSource.class);
    private final UserManagementService svc = new UserManagementService(
            userRepo, auditRepo, passwordEncoder, provisioning, tokenRepo, accountService, messageSource);

    // -------------------------------------------------------
    // listUsers
    // -------------------------------------------------------
    @Nested
    @DisplayName("listUsers")
    class ListUsers {

        @Test
        @DisplayName("returns page of UserSummaryResponse mapped from User entities")
        void returns_paged_user_summaries() {
            User u = User.builder()
                    .id(1L).email("a@b.com").firstname("Alice").lastname("Smith")
                    .role(Role.USER).tenantId("t1").accountEnabled(true).build();
            Pageable pageable = PageRequest.of(0, 10);
            when(userRepo.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u)));

            Page<UserSummaryResponse> result = svc.listUsers(pageable);

            assertThat(result.getContent()).hasSize(1);
            UserSummaryResponse r = result.getContent().get(0);
            assertThat(r.id()).isEqualTo(1L);
            assertThat(r.email()).isEqualTo("a@b.com");
            assertThat(r.role()).isEqualTo(Role.USER);
            assertThat(r.tenantId()).isEqualTo("t1");
            assertThat(r.accountEnabled()).isTrue();
        }

        @Test
        @DisplayName("returns empty page when no users exist")
        void returns_empty_page_when_no_users() {
            Pageable pageable = PageRequest.of(0, 10);
            when(userRepo.findAll(pageable)).thenReturn(Page.empty());

            Page<UserSummaryResponse> result = svc.listUsers(pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // -------------------------------------------------------
    // createUser (POST /api/v1/auth/users — ADMIN only)
    // -------------------------------------------------------
    @Nested
    @DisplayName("createUser")
    class CreateUser {

        private AdminCreateUserRequest request(Role role, String tenantId) {
            return new AdminCreateUserRequest(
                    "New", "User", "new.user@x.com", "Password1!", role, tenantId);
        }

        @Test
        @DisplayName("creates SELLER with encoded password, default tenant, audit and profile provisioning")
        void creates_seller_with_defaults() {
            when(userRepo.existsByEmail("new.user@x.com")).thenReturn(false);
            when(passwordEncoder.encode("Password1!")).thenReturn("$2a$10$encoded");
            when(userRepo.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(42L);
                return u;
            });

            UserSummaryResponse result = svc.createUser("admin@x.com", request(Role.SELLER, null));

            assertThat(result.id()).isEqualTo(42L);
            assertThat(result.role()).isEqualTo(Role.SELLER);
            assertThat(result.tenantId()).isEqualTo("default");
            assertThat(result.accountEnabled()).isTrue();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepo).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getPassword()).isEqualTo("$2a$10$encoded");
            assertThat(saved.isAccountLocked()).isFalse();

            verify(provisioning).ensureCustomerProfile(saved);

            ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditRepo).save(logCaptor.capture());
            assertThat(logCaptor.getValue().getChangedBy()).isEqualTo("admin@x.com");
            assertThat(logCaptor.getValue().getTargetUserId()).isEqualTo("42");
            assertThat(logCaptor.getValue().getNewRole()).isEqualTo(Role.SELLER);
        }

        @Test
        @DisplayName("admin may create another ADMIN (public register blocks this — admin path allows it)")
        void creates_admin_role() {
            when(userRepo.existsByEmail("new.user@x.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            when(userRepo.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(7L);
                return u;
            });

            UserSummaryResponse result = svc.createUser("admin@x.com", request(Role.ADMIN, "default"));

            assertThat(result.role()).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("keeps explicit tenantId when provided")
        void keeps_explicit_tenant() {
            when(userRepo.existsByEmail("new.user@x.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            when(userRepo.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(8L);
                return u;
            });

            UserSummaryResponse result = svc.createUser("admin@x.com", request(Role.USER, "tenant-x"));

            assertThat(result.tenantId()).isEqualTo("tenant-x");
        }

        @Test
        @DisplayName("throws UserAlreadyExistsException for duplicate email — nothing persisted")
        void rejects_duplicate_email() {
            when(userRepo.existsByEmail("new.user@x.com")).thenReturn(true);
            when(messageSource.getMessage(eq("auth.user.already.exists"), any(), any()))
                    .thenReturn("A user with email [new.user@x.com] already exists.");

            assertThatThrownBy(() -> svc.createUser("admin@x.com", request(Role.USER, null)))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userRepo, never()).save(any());
            verify(auditRepo, never()).save(any());
            verify(provisioning, never()).ensureCustomerProfile(any());
        }
    }

    // -------------------------------------------------------
    // updateUser (PATCH /api/v1/auth/users/{userId} — ADMIN only)
    // -------------------------------------------------------
    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        private User target() {
            return User.builder().id(5L).email("old@x.com")
                    .firstname("Old").lastname("Name").role(Role.USER).build();
        }

        @Test
        @DisplayName("updates name only — no token revocation, profile synced, audit written")
        void updates_name_only() {
            User target = target();
            when(userRepo.findById(5L)).thenReturn(Optional.of(target));
            when(userRepo.save(target)).thenReturn(target);

            UserSummaryResponse result = svc.updateUser("admin@x.com", 5L,
                    new AdminUpdateUserRequest("New", "Person", null));

            assertThat(result.firstname()).isEqualTo("New");
            assertThat(result.lastname()).isEqualTo("Person");
            assertThat(target.getEmail()).isEqualTo("old@x.com");
            verify(provisioning).syncCustomerProfile(target);
            verify(tokenRepo, never()).revokeAllUserTokens(any());
            verify(auditRepo).save(any());
        }

        @Test
        @DisplayName("email change revokes the target's sessions")
        void email_change_revokes_sessions() {
            User target = target();
            when(userRepo.findById(5L)).thenReturn(Optional.of(target));
            when(userRepo.existsByEmail("new@x.com")).thenReturn(false);
            when(userRepo.save(target)).thenReturn(target);

            svc.updateUser("admin@x.com", 5L,
                    new AdminUpdateUserRequest(null, null, "new@x.com"));

            assertThat(target.getEmail()).isEqualTo("new@x.com");
            verify(tokenRepo).revokeAllUserTokens(5L);
        }

        @Test
        @DisplayName("throws UserAlreadyExistsException when new email is taken")
        void rejects_taken_email() {
            User target = target();
            when(userRepo.findById(5L)).thenReturn(Optional.of(target));
            when(userRepo.existsByEmail("taken@x.com")).thenReturn(true);
            when(messageSource.getMessage(eq("auth.account.email.taken"), any(), any()))
                    .thenReturn("That email address is already in use by another account.");

            assertThatThrownBy(() -> svc.updateUser("admin@x.com", 5L,
                    new AdminUpdateUserRequest(null, null, "taken@x.com")))
                    .isInstanceOf(UserAlreadyExistsException.class);

            verify(userRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when no field is provided")
        void rejects_empty_request() {
            assertThatThrownBy(() -> svc.updateUser("admin@x.com", 5L,
                    new AdminUpdateUserRequest(null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(userRepo, never()).findById(any());
        }

        @Test
        @DisplayName("throws AuthUserNotFoundException when target does not exist")
        void throws_not_found() {
            when(userRepo.findById(99L)).thenReturn(Optional.empty());
            when(messageSource.getMessage(eq("auth.user.not.found"), any(), any()))
                    .thenReturn("User with email [99] not found.");

            assertThatThrownBy(() -> svc.updateUser("admin@x.com", 99L,
                    new AdminUpdateUserRequest("A", null, null)))
                    .isInstanceOf(AuthUserNotFoundException.class);
        }
    }

    // -------------------------------------------------------
    // setUserStatus (PATCH /api/v1/auth/users/{userId}/status — ADMIN only)
    // -------------------------------------------------------
    @Nested
    @DisplayName("setUserStatus")
    class SetUserStatus {

        @Test
        @DisplayName("deactivation disables the account and revokes all tokens")
        void deactivation_revokes_tokens() {
            User target = User.builder().id(6L).email("t@x.com")
                    .role(Role.USER).accountEnabled(true).build();
            when(userRepo.findById(6L)).thenReturn(Optional.of(target));
            when(userRepo.save(target)).thenReturn(target);

            UserSummaryResponse result = svc.setUserStatus("admin@x.com", 1L, 6L, false);

            assertThat(result.accountEnabled()).isFalse();
            assertThat(target.isAccountEnabled()).isFalse();
            verify(tokenRepo).revokeAllUserTokens(6L);
            verify(auditRepo).save(any());
        }

        @Test
        @DisplayName("reactivation enables the account without touching tokens")
        void reactivation_does_not_revoke() {
            User target = User.builder().id(6L).email("t@x.com")
                    .role(Role.USER).accountEnabled(false).build();
            when(userRepo.findById(6L)).thenReturn(Optional.of(target));
            when(userRepo.save(target)).thenReturn(target);

            UserSummaryResponse result = svc.setUserStatus("admin@x.com", 1L, 6L, true);

            assertThat(result.accountEnabled()).isTrue();
            verify(tokenRepo, never()).revokeAllUserTokens(any());
        }

        @Test
        @DisplayName("no-op when status already matches — nothing saved, no audit")
        void noop_when_status_unchanged() {
            User target = User.builder().id(6L).email("t@x.com")
                    .role(Role.USER).accountEnabled(true).build();
            when(userRepo.findById(6L)).thenReturn(Optional.of(target));

            svc.setUserStatus("admin@x.com", 1L, 6L, true);

            verify(userRepo, never()).save(any());
            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws AdminActionNotAllowedException when admin deactivates self")
        void rejects_self_deactivation() {
            when(messageSource.getMessage(eq("auth.admin.self.deactivate.denied"), any(), any()))
                    .thenReturn("Admins cannot deactivate their own account.");

            assertThatThrownBy(() -> svc.setUserStatus("admin@x.com", 1L, 1L, false))
                    .isInstanceOf(AdminActionNotAllowedException.class);

            verify(userRepo, never()).findById(any());
        }

        @Test
        @DisplayName("throws AuthUserNotFoundException when target does not exist")
        void throws_not_found() {
            when(userRepo.findById(99L)).thenReturn(Optional.empty());
            when(messageSource.getMessage(eq("auth.user.not.found"), any(), any()))
                    .thenReturn("User with email [99] not found.");

            assertThatThrownBy(() -> svc.setUserStatus("admin@x.com", 1L, 99L, false))
                    .isInstanceOf(AuthUserNotFoundException.class);
        }
    }

    // -------------------------------------------------------
    // deleteUser (DELETE /api/v1/auth/users/{userId} — ADMIN only)
    // -------------------------------------------------------
    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("delegates soft-delete to AccountService and writes audit")
        void soft_deletes_via_account_service() {
            User target = User.builder().id(7L).email("victim@x.com").role(Role.SELLER).build();
            when(userRepo.findById(7L)).thenReturn(Optional.of(target));

            svc.deleteUser("admin@x.com", 1L, 7L);

            verify(accountService).softDeleteAndAnonymize(target);

            ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditRepo).save(logCaptor.capture());
            assertThat(logCaptor.getValue().getChangedBy()).isEqualTo("admin@x.com");
            assertThat(logCaptor.getValue().getTargetUserId()).isEqualTo("7");
            assertThat(logCaptor.getValue().getPreviousRole()).isEqualTo(Role.SELLER);
        }

        @Test
        @DisplayName("throws AdminActionNotAllowedException when admin deletes self")
        void rejects_self_delete() {
            when(messageSource.getMessage(eq("auth.admin.self.delete.denied"), any(), any()))
                    .thenReturn("Admins cannot delete their own account.");

            assertThatThrownBy(() -> svc.deleteUser("admin@x.com", 1L, 1L))
                    .isInstanceOf(AdminActionNotAllowedException.class);

            verify(accountService, never()).softDeleteAndAnonymize(any());
        }

        @Test
        @DisplayName("throws AuthUserNotFoundException when target does not exist")
        void throws_not_found() {
            when(userRepo.findById(99L)).thenReturn(Optional.empty());
            when(messageSource.getMessage(eq("auth.user.not.found"), any(), any()))
                    .thenReturn("User with email [99] not found.");

            assertThatThrownBy(() -> svc.deleteUser("admin@x.com", 1L, 99L))
                    .isInstanceOf(AuthUserNotFoundException.class);

            verify(accountService, never()).softDeleteAndAnonymize(any());
        }
    }

    // -------------------------------------------------------
    // updateRole
    // -------------------------------------------------------
    @Nested
    @DisplayName("updateRole")
    class UpdateRole {

        @Test
        @DisplayName("promotes USER to SELLER and writes audit log")
        void promotes_user_and_writes_audit() {
            User target = User.builder().id(2L).email("seller@x.com").role(Role.USER).build();
            when(userRepo.findById(2L)).thenReturn(Optional.of(target));
            when(userRepo.save(target)).thenReturn(target);

            UserSummaryResponse result = svc.updateRole("admin@x.com", 1L, 2L, Role.SELLER);

            assertThat(result.role()).isEqualTo(Role.SELLER);
            assertThat(target.getRole()).isEqualTo(Role.SELLER);

            ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditRepo).save(logCaptor.capture());
            AuditLog log = logCaptor.getValue();
            assertThat(log.getChangedBy()).isEqualTo("admin@x.com");
            assertThat(log.getTargetUserId()).isEqualTo("2");
            assertThat(log.getPreviousRole()).isEqualTo(Role.USER);
            assertThat(log.getNewRole()).isEqualTo(Role.SELLER);
        }

        @Test
        @DisplayName("demotes SELLER to USER and writes audit log")
        void demotes_seller_and_writes_audit() {
            User target = User.builder().id(3L).email("seller@x.com").role(Role.SELLER).build();
            when(userRepo.findById(3L)).thenReturn(Optional.of(target));
            when(userRepo.save(target)).thenReturn(target);

            svc.updateRole("admin@x.com", 1L, 3L, Role.USER);

            assertThat(target.getRole()).isEqualTo(Role.USER);
            verify(auditRepo).save(any());
        }

        @Test
        @DisplayName("no-op and no audit when new role equals current role")
        void noop_when_same_role() {
            User target = User.builder().id(4L).email("u@x.com").role(Role.USER).build();
            when(userRepo.findById(4L)).thenReturn(Optional.of(target));

            svc.updateRole("admin@x.com", 1L, 4L, Role.USER);

            verify(userRepo, never()).save(any());
            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when admin tries to change own role")
        void rejects_self_role_change() {
            assertThatThrownBy(() -> svc.updateRole("admin@x.com", 1L, 1L, Role.USER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("own role");

            verify(userRepo, never()).findById(any());
            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws AuthUserNotFoundException when target user does not exist")
        void throws_not_found_when_target_missing() {
            when(userRepo.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> svc.updateRole("admin@x.com", 1L, 99L, Role.SELLER))
                    .isInstanceOf(AuthUserNotFoundException.class);

            verify(auditRepo, never()).save(any());
        }
    }
}
