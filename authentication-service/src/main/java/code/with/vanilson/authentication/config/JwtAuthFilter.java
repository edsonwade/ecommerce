package code.with.vanilson.authentication.config;

import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JwtAuthFilter — Security Filter
 * <p>
 * Validates JWTs on every request. Exception-safe: expired/invalid JWTs
 * return 401 JSON instead of propagating as HTTP 500.
 * Uses JTI-based DB lookup (not full JWT string).
 * No hard cast to domain User — works with any UserDetails implementation.
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
    private final ObjectMapper           objectMapper;

    @Override
    public void doFilterInternal(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        try {
            String email = jwtService.extractSubject(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                String  jti        = jwtService.extractJti(jwt);
                boolean tokenValid = tokenRepository.findByJti(jti)
                        .map(t -> !t.isExpired() && !t.isRevoked())
                        .orElse(false);

                if (jwtService.isTokenValid(jwt, userDetails) && tokenValid) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            log.warn("[JwtAuthFilter] Expired token: {}", ex.getMessage());
            writeJsonError(response, 401, "auth.jwt.expired", "The JWT token has expired. Please authenticate again.");
        } catch (JwtException ex) {
            SecurityContextHolder.clearContext();
            log.warn("[JwtAuthFilter] Invalid token: {}", ex.getMessage());
            writeJsonError(response, 401, "auth.jwt.invalid", "Invalid or malformed JWT token.");
        }
    }

    private void writeJsonError(HttpServletResponse response, int status,
                                 String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status",    status);
        body.put("errorCode", errorCode);
        body.put("message",   message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
