package code.with.vanilson.authentication.application;

import code.with.vanilson.authentication.domain.AuditLog;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.exception.AuthUserNotFoundException;
import code.with.vanilson.authentication.infrastructure.AuditLogRepository;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserManagementService — unit tests")
class UserManagementServiceTest {

    private final UserRepository     userRepo  = mock(UserRepository.class);
    private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
    private final UserManagementService svc    = new UserManagementService(userRepo, auditRepo);

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
