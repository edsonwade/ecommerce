package code.with.vanilson.configservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * ConfigServerSecurityConfig — secures the Spring Cloud Config Server endpoints.
 * <p>
 * All config endpoints require HTTP basic auth except:
 * - /actuator/health — for health checks (load balancer probes)
 * - /actuator/info  — non-sensitive info
 * <p>
 * Credentials: username=config, password from CONFIG_SERVER_PASSWORD env var.
 * All client services must set spring.config.import with credentials:
 *   optional:configserver:http://config:${CONFIG_SERVER_PASSWORD}@config-service:8888
 * </p>
 */
@Configuration
@Order(0)
@EnableWebFluxSecurity
public class ConfigServerSecurityConfig {

    @Value("${config.server.username:config}")
    private String username;

    @Value("${CONFIG_SERVER_PASSWORD:changeme-set-in-env}")
    private String password;

    @Bean
    public SecurityWebFilterChain configServerSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyExchange().authenticated()
                )
                .httpBasic(basic -> {})
                .build();
    }

    @Bean
    public MapReactiveUserDetailsService configServerUserDetailsService(PasswordEncoder encoder) {
        UserDetails configUser = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("CONFIG_CLIENT")
                .build();
        return new MapReactiveUserDetailsService(configUser);
    }

    @Bean
    public PasswordEncoder configServerPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
