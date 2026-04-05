package code.with.vanilson.authentication.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JpaConfig — isolated @Configuration for JPA cross-cutting concerns.
 * <p>
 * @EnableJpaAuditing was previously on SecurityConfig. That caused every
 * @WebMvcTest slice to fail loading SecurityConfig (JPA infrastructure is
 * not available in the web slice), which made Spring Boot fall back to its
 * default security configuration — CSRF enabled, all endpoints protected —
 * returning HTTP 403 for every request in controller tests.
 * <p>
 * Placing @EnableJpaAuditing here (a plain @Configuration picked up only
 * when the full application context is present) keeps the JPA auditing
 * behaviour intact in production while allowing @WebMvcTest to load
 * SecurityConfig cleanly.
 *
 * @author vamuhong
 * @version 1.0
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
