package code.with.vanilson.orderservice.config;

import code.with.vanilson.orderservice.internal.InternalTokenFilter;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class OrderSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalTokenFilter internalTokenFilter;

    // Unauthenticated endpoints: health probes, Prometheus scrape, API docs and the
    // internal service-to-service purchase-verification API (F7). Only specific actuator
    // endpoints are opened — never /actuator/** (env/heapdump stay protected).
    //
    // /internal/** is permitAll at the Spring Security layer BY DESIGN — the InternalTokenFilter
    // (X-Internal-Token shared secret) is the authenticator for that path, keeping the JWT filter
    // off the S2S call (F7 defence-in-depth Layer 3). It is deliberately NOT added to the gateway
    // public-paths, so an anonymous external caller is still stopped at the gateway (Layer 1); the
    // real caller (product-service) reaches this service directly on services-net.
    static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/api-docs/**",
        "/api/v1/orders/internal/**"
    };

    @Bean
    public SecurityFilterChain orderSecurityChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers("/api/v1/orders/*/events").authenticated()
                .anyRequest().authenticated())
            // InternalTokenFilter runs FIRST so /internal/** is authenticated by the shared secret
            // before authorization; on non-internal paths it self-skips (shouldNotFilter).
            .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
