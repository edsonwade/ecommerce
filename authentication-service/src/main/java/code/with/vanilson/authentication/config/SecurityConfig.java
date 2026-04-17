package code.with.vanilson.authentication.config;

import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SecurityConfig — Infrastructure Layer (Security)
 * <p>
 * Configures the Spring Security filter chain with:
 * - STATELESS session (JWT-based)
 * - AuthenticationEntryPoint: returns 401 JSON + WWW-Authenticate for unauthenticated requests
 * - Actuator restricted to /health only (not /env, /heapdump, /beans)
 * - Explicit CORS configuration
 * - BCrypt password encoder (cost factor 12)
 * - DaoAuthenticationProvider backed by UserDetailsServiceImpl
 * - OpenAPI Swagger with Bearer auth scheme
 * <p>
 * NOTE — @EnableJpaAuditing was intentionally moved to JpaConfig so that
 * @WebMvcTest slices can load SecurityConfig without JPA infrastructure.
 * </p>
 *
 * @author vamuhong
 * @version 2.1
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter          jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final ObjectMapper           objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          UserDetailsServiceImpl userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtAuthFilter      = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper       = objectMapper;
    }

    // @Order(1) ensures this chain is matched by FilterChainProxy before Spring Boot's
    // auto-configured default chain (@Order(Integer.MAX_VALUE - 5)), which otherwise wins
    // in @WebMvcTest when both chains coexist (conditional evaluated before our bean exists).
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    // /refresh is permit-all: a revoked refresh token must reach
                    // RefreshTokenService.rotate() so it can return the specific
                    // "auth.token.refresh.invalid" error code. JwtAuthFilter handles
                    // cryptographically invalid / expired tokens before the controller.
                    "/api/v1/auth/refresh",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/actuator/health",
                    "/actuator/health/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                // Return 401 JSON + WWW-Authenticate instead of Spring Security's default 403
                // for every unauthenticated request to a protected endpoint (e.g. /logout).
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                            "Bearer realm=\"Authentication API\"");
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("timestamp",  Instant.now().toString());
                    body.put("status",     401);
                    body.put("errorCode",  "auth.unauthorized");
                    body.put("message",    "Authentication required. Please provide a valid Bearer token.");
                    body.put("path",       request.getRequestURI());
                    objectMapper.writeValue(response.getOutputStream(), body);
                })
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public OpenAPI authServiceOpenAPI() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Authentication Service API")
                        .description("SaaS eCommerce — JWT Authentication & Authorization")
                        .version("v2.0"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
