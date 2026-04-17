package code.with.vanilson.authentication.config;

import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.infrastructure.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "Admin@123!";

    private final UserRepository  repo;
    private final PasswordEncoder encoder;
    private final String          adminEmail;
    private final String          adminPassword;
    private final String          systemTenant;

    public AdminBootstrapRunner(
            UserRepository repo,
            PasswordEncoder encoder,
            @Value("${app.admin.email:admin@platform.com}") String adminEmail,
            @Value("${app.admin.password:Admin@123!}") String adminPassword,
            @Value("${app.admin.tenant-id:system}") String systemTenant) {
        this.repo          = repo;
        this.encoder       = encoder;
        this.adminEmail    = adminEmail;
        this.adminPassword = adminPassword;
        this.systemTenant  = systemTenant;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.countByRole(Role.ADMIN) > 0) {
            log.info("[AdminBootstrap] ADMIN user already exists — skipping seed");
            return;
        }

        User admin = User.builder()
                .firstname("Platform")
                .lastname("Admin")
                .email(adminEmail)
                .password(encoder.encode(adminPassword))
                .role(Role.ADMIN)
                .tenantId(systemTenant)
                .accountEnabled(true)
                .accountLocked(false)
                .build();

        repo.save(admin);

        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.warn("[SECURITY] Admin seeded with DEFAULT credentials — change immediately via PATCH /api/v1/auth/users/{id}/role or env ADMIN_PASSWORD");
        }
        log.info("[AdminBootstrap] ADMIN user seeded: email={} tenantId={}", adminEmail, systemTenant);
    }
}
