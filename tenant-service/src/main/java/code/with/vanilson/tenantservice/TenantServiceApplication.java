package code.with.vanilson.tenantservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * TenantServiceApplication — Entry Point
 * <p>
 * Bootstraps the Tenant Service (Phase 4 — SaaS Multi-Tenancy Layer).
 * @EnableJpaAuditing is activated by TenantServiceConfig.
 *
 * @author vamuhong
 * @version 4.0
 */
@SpringBootApplication
@EnableDiscoveryClient
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
