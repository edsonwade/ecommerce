package code.with.vanilson.authentication.config;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("AdminBootstrapRunner — unit tests")
class AdminBootstrapRunnerTest {

    private final UserRepository repo    = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    private AdminBootstrapRunner runner(String email, String password, String tenant) {
        return new AdminBootstrapRunner(repo, encoder, email, password, tenant);
    }

    @Nested
    @DisplayName("when no ADMIN exists")
    class WhenNoAdminExists {

        @Test
        @DisplayName("creates ADMIN user with provided credentials and tenant")
        void creates_admin_when_none_exists() throws Exception {
            when(repo.countByRole(Role.ADMIN)).thenReturn(0L);
            when(encoder.encode("Admin@123!")).thenReturn("hashed-password");

            runner("admin@platform.com", "Admin@123!", "system").run(null);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(repo).save(captor.capture());
            User saved = captor.getValue();

            assertThat(saved.getEmail()).isEqualTo("admin@platform.com");
            assertThat(saved.getPassword()).isEqualTo("hashed-password");
            assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
            assertThat(saved.getTenantId()).isEqualTo("system");
            assertThat(saved.isAccountEnabled()).isTrue();
            assertThat(saved.isAccountLocked()).isFalse();
        }

        @Test
        @DisplayName("encodes the password before saving")
        void encodes_password_before_saving() throws Exception {
            when(repo.countByRole(Role.ADMIN)).thenReturn(0L);
            when(encoder.encode("secret")).thenReturn("encoded-secret");

            runner("a@b.com", "secret", "t1").run(null);

            verify(encoder).encode("secret");
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(repo).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("encoded-secret");
        }
    }

    @Nested
    @DisplayName("when ADMIN already exists")
    class WhenAdminAlreadyExists {

        @Test
        @DisplayName("does NOT create another admin user")
        void noop_when_admin_exists() throws Exception {
            when(repo.countByRole(Role.ADMIN)).thenReturn(1L);

            runner("admin@platform.com", "Admin@123!", "system").run(null);

            verify(repo, never()).save(any());
        }

        @Test
        @DisplayName("does NOT encode any password")
        void does_not_encode_password_when_skipping() throws Exception {
            when(repo.countByRole(Role.ADMIN)).thenReturn(2L);

            runner("admin@platform.com", "Admin@123!", "system").run(null);

            verify(encoder, never()).encode(anyString());
        }
    }
}
