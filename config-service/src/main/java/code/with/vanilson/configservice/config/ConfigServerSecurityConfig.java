package code.with.vanilson.configservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ConfigServerSecurityConfig — secures the Spring Cloud Config Server endpoints.
 * <p>
 * All config endpoints require HTTP basic auth except:
 * - /actuator/health — for health checks (load balancer probes)
 * - /actuator/info  — non-sensitive info
 * </p>
 */
@Configuration
@Order(0)
@EnableWebSecurity
public class ConfigServerSecurityConfig {

    @Value("${config.server.username:config}")
    private String username;

    @Value("${CONFIG_SERVER_PASSWORD:changeme-set-in-env}")
    private String password;

    @Bean
    public SecurityFilterChain configServerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public InMemoryUserDetailsManager configServerUserDetailsService(PasswordEncoder encoder) {
        UserDetails configUser = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("CONFIG_CLIENT")
                .build();
        return new InMemoryUserDetailsManager(configUser);
    }

    @Bean
    public PasswordEncoder configServerPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
