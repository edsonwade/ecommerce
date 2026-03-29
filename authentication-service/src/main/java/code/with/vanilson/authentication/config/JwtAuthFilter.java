package code.with.vanilson.authentication.config;

import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import code.with.vanilson.authentication.infrastructure.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter — Security Filter
 * <p>
 * Validates JWTs on every request within the auth-service itself.
 * Note: at the system level, the Gateway's JwtAuthenticationFilter handles JWT
 * validation for ALL services. This filter protects auth-service's own secured
 * endpoints (e.g., GET /api/v1/auth/me, POST /api/v1/auth/logout).
 * <p>
 * Single Responsibility (SOLID-S): only JWT extraction + context population.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService             jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenRepository        tokenRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt   = authHeader.substring(7);
        String email = jwtService.extractSubject(jwt);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            boolean tokenValid = tokenRepository.findByTokenValue(jwt)
                    .map(token -> !token.isExpired() && !token.isRevoked())
                    .orElse(false);

            if (jwtService.isTokenValid(jwt, (code.with.vanilson.authentication.domain.User) userDetails)
                    && tokenValid) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
