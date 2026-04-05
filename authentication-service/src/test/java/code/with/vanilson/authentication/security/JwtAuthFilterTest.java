package code.with.vanilson.authentication.security;

import code.with.vanilson.authentication.config.JwtAuthFilter;
import code.with.vanilson.authentication.domain.Role;
import code.with.vanilson.authentication.domain.Token;
import code.with.vanilson.authentication.domain.User;
import code.with.vanilson.authentication.domain.UserDetailsAdapter;
import code.with.vanilson.authentication.infrastructure.JwtService;
import code.with.vanilson.authentication.infrastructure.TokenRepository;
import code.with.vanilson.authentication.infrastructure.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtAuthFilterTest — unit tests for the JWT security filter.
 * Validates: pass-through with no token, SecurityContext population,
 * expired/malformed token 401 JSON responses, revoked-token handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter Unit Tests")
class JwtAuthFilterTest {

    @Mock JwtService             jwtService;
    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock TokenRepository        tokenRepository;

    @InjectMocks JwtAuthFilter filter;

    private ObjectMapper          objectMapper;
    private MockFilterChain       chain;
    private UserDetailsAdapter    userDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // JwtAuthFilter needs ObjectMapper — inject it manually since @InjectMocks may miss it
        try {
            var field = JwtAuthFilter.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(filter, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject ObjectMapper into filter", e);
        }

        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();

        User testUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .password("encoded")
                .role(Role.USER)
                .tenantId("default")
                .accountEnabled(true)
                .accountLocked(false)
                .build();
        userDetails = new UserDetailsAdapter(testUser);
    }

    // -------------------------------------------------------
    // No Authorization header
    // -------------------------------------------------------
    @Nested
    @DisplayName("No Authorization header")
    class NoAuthHeader {

        @Test
        @DisplayName("request without Authorization header passes through filter chain")
        void noHeaderPassesThrough() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            assertThat(chain.getRequest()).isNotNull(); // filter chain was invoked
            assertThat(res.getStatus()).isEqualTo(200); // no error written
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("request with non-Bearer Authorization header passes through")
        void basicAuthHeaderPassesThrough() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            assertThat(chain.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(jwtService, never()).extractSubject(anyString());
        }
    }

    // -------------------------------------------------------
    // Valid token — SecurityContext populated
    // -------------------------------------------------------
    @Nested
    @DisplayName("Valid JWT token")
    class ValidToken {

        @Test
        @DisplayName("valid active token sets Authentication in SecurityContext")
        void validTokenSetsSecurityContext() throws Exception {
            String jwt = "valid.jwt.token";
            String jti = "aaaa-bbbb-cccc-dddd";
            Token activeToken = Token.builder()
                    .jti(jti).tokenType(Token.TokenType.BEARER)
                    .expired(false).revoked(false).build();

            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer " + jwt);
            MockHttpServletResponse res = new MockHttpServletResponse();

            when(jwtService.extractSubject(jwt)).thenReturn("user@example.com");
            when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
            when(jwtService.extractJti(jwt)).thenReturn(jti);
            when(tokenRepository.findByJti(jti)).thenReturn(Optional.of(activeToken));
            when(jwtService.isTokenValid(jwt, userDetails)).thenReturn(true);

            filter.doFilterInternal(req, res, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                    .isEqualTo("user@example.com");
            assertThat(res.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("token not found in DB (revoked) — passes through but no auth set")
        void tokenNotInDbNoAuthSet() throws Exception {
            String jwt = "valid.signature.token";
            String jti = "not-in-db-jti";

            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer " + jwt);
            MockHttpServletResponse res = new MockHttpServletResponse();

            when(jwtService.extractSubject(jwt)).thenReturn("user@example.com");
            when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
            when(jwtService.extractJti(jwt)).thenReturn(jti);
            when(tokenRepository.findByJti(jti)).thenReturn(Optional.empty()); // not in DB

            filter.doFilterInternal(req, res, chain);

            // Filter should still call chain — it just doesn't set auth
            assertThat(chain.getRequest()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("revoked DB token — no auth set, chain continues")
        void revokedDbTokenNoAuthSet() throws Exception {
            String jwt = "revoked.jwt.token";
            String jti = "revoked-jti";
            Token revokedToken = Token.builder()
                    .jti(jti).tokenType(Token.TokenType.BEARER)
                    .expired(true).revoked(true).build();

            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer " + jwt);
            MockHttpServletResponse res = new MockHttpServletResponse();

            when(jwtService.extractSubject(jwt)).thenReturn("user@example.com");
            when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
            when(jwtService.extractJti(jwt)).thenReturn(jti);
            when(tokenRepository.findByJti(jti)).thenReturn(Optional.of(revokedToken));
            when(jwtService.isTokenValid(jwt, userDetails)).thenReturn(false);

            filter.doFilterInternal(req, res, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // -------------------------------------------------------
    // Expired JWT
    // -------------------------------------------------------
    @Nested
    @DisplayName("Expired JWT token")
    class ExpiredToken {

        @Test
        @DisplayName("expired JWT returns 401 JSON response with errorCode auth.jwt.expired")
        void expiredJwtReturns401() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer expired.token");
            MockHttpServletResponse res = new MockHttpServletResponse();

            // Build a minimal ExpiredJwtException
            DefaultClaims claims = new DefaultClaims(Map.of("sub", "user@example.com"));
            ExpiredJwtException ex = new ExpiredJwtException(null, claims, "JWT expired");
            when(jwtService.extractSubject("expired.token")).thenThrow(ex);

            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(res.getContentType()).contains("application/json");

            Map<?, ?> body = objectMapper.readValue(res.getContentAsString(), Map.class);
            assertThat(body.get("status")).isEqualTo(401);
            assertThat(body.get("errorCode")).isEqualTo("auth.jwt.expired");
            assertThat(body.get("message")).isEqualTo("The JWT token has expired. Please authenticate again.");
            assertThat(body.get("timestamp")).isNotNull();

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("expired JWT clears SecurityContext before writing response")
        void expiredJwtClearsSecurityContext() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer expired.token");
            MockHttpServletResponse res = new MockHttpServletResponse();

            DefaultClaims claims = new DefaultClaims(Map.of("sub", "x@x.com"));
            when(jwtService.extractSubject("expired.token"))
                    .thenThrow(new ExpiredJwtException(null, claims, "expired"));

            filter.doFilterInternal(req, res, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    // -------------------------------------------------------
    // Malformed / tampered JWT
    // -------------------------------------------------------
    @Nested
    @DisplayName("Malformed / tampered JWT token")
    class MalformedToken {

        @Test
        @DisplayName("malformed JWT returns 401 JSON with errorCode auth.jwt.invalid")
        void malformedJwtReturns401() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer not.a.real.jwt");
            MockHttpServletResponse res = new MockHttpServletResponse();

            when(jwtService.extractSubject("not.a.real.jwt"))
                    .thenThrow(new JwtException("Malformed JWT"));

            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(401);

            Map<?, ?> body = objectMapper.readValue(res.getContentAsString(), Map.class);
            assertThat(body.get("status")).isEqualTo(401);
            assertThat(body.get("errorCode")).isEqualTo("auth.jwt.invalid");
            assertThat(body.get("message")).isEqualTo("Invalid or malformed JWT token.");
        }

        @Test
        @DisplayName("tampered JWT signature throws JwtException and returns 401")
        void tamperedSignatureReturns401() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer hdr.payload.TAMPERED");
            MockHttpServletResponse res = new MockHttpServletResponse();

            when(jwtService.extractSubject("hdr.payload.TAMPERED"))
                    .thenThrow(new JwtException("Signature mismatch"));

            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(401);
            Map<?, ?> body = objectMapper.readValue(res.getContentAsString(), Map.class);
            assertThat(body.get("errorCode")).isEqualTo("auth.jwt.invalid");
        }

        @Test
        @DisplayName("malformed JWT clears SecurityContext")
        void malformedJwtClearsSecurityContext() throws Exception {
            MockHttpServletRequest req  = new MockHttpServletRequest();
            req.addHeader("Authorization", "Bearer garbage");
            MockHttpServletResponse res = new MockHttpServletResponse();

            when(jwtService.extractSubject("garbage"))
                    .thenThrow(new JwtException("invalid"));

            filter.doFilterInternal(req, res, chain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
