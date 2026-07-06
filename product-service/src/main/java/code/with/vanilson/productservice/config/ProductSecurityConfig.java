package code.with.vanilson.productservice.config;

import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class ProductSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Infrastructure endpoints opened without authentication: health probes,
    // Prometheus scrape and API docs. Only specific actuator endpoints are
    // listed — never /actuator/** (env/heapdump stay protected). Prometheus
    // scrapes /actuator/prometheus without credentials, so it must be public
    // or every scrape fails with 401.
    static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health/**",
        "/actuator/prometheus",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/api-docs/**"
    };

    @Bean
    public SecurityFilterChain productSecurityChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // Seller's own catalogue must be authenticated — matched BEFORE the public
                // GET /** rule below (first match wins). Role is enforced via @PreAuthorize.
                .requestMatchers(HttpMethod.GET, "/api/v1/products/mine").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );
        return http.build();
    }
}
