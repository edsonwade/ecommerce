package code.with.vanilson.gatewayservice.filter;

import code.with.vanilson.gatewayservice.exception.JwtAuthenticationException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * JwtAuthenticationFilterTest
 * <p>
 * Framework: JUnit 5 + Mockito + Reactor Test + Spring MockServerWebExchange.
 * Exercises the HS256 fallback path — tokens are generated in-test with the
 * same shared secret the filter is constructed with.
 *
 * @author vamuhong
 * @version 2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — Unit Tests")
class JwtAuthenticationFilterTest {

    /** 32 random bytes, base64-encoded — minimum key size for HS256. */
    private static final String SECRET_BASE64 =
            Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes());

    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_BASE64));

    private static final String PROTECTED_PATH = "/api/v1/orders";

    @Mock private MessageSource      messageSource;
    @Mock private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        filter = new JwtAuthenticationFilter(
                messageSource,
                "",               // no RSA public key → HS256 fallback
                SECRET_BASE64,
                List.of("/api/v1/auth/**", "/actuator/**"));

        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String token(String userId, String tenantId, String role, Date expiry) {
        return Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis() - 1_000))
                .expiration(expiry)
                .signWith(SIGNING_KEY)
                .compact();
    }

    private String validToken() {
        return token("user-42", "tenant-001", "USER",
                new Date(System.currentTimeMillis() + 60_000));
    }

    private MockServerWebExchange exchangeFor(String path, String authHeader) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(HttpMethod.GET, path);
        if (authHeader != null) {
            builder.header("Authorization", authHeader);
        }
        return MockServerWebExchange.from(builder.build());
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Public paths — bypass JWT validation")
    class PublicPaths {

        @Test @DisplayName("should skip validation for /api/v1/auth/login")
        void shouldSkipForAuthLogin() {
            MockServerWebExchange exchange = exchangeFor("/api/v1/auth/login", null);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(any());
        }

        @Test @DisplayName("should skip validation for nested actuator path")
        void shouldSkipForActuator() {
            MockServerWebExchange exchange = exchangeFor("/actuator/health", null);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(any());
        }

        @Test @DisplayName("should NOT treat /api/v1/auth-admin/* as public (startsWith bypass regression)")
        void shouldNotBypassForPrefixSibling() {
            MockServerWebExchange exchange = exchangeFor("/api/v1/auth-admin/secret", null);

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(JwtAuthenticationException.class);

            verify(chain, never()).filter(any());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Missing / malformed Authorization header")
    class MissingHeader {

        @Test @DisplayName("should reject protected path without Authorization header")
        void shouldRejectMissingHeader() {
            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, null);

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(JwtAuthenticationException.class);

            verify(chain, never()).filter(any());
        }

        @Test @DisplayName("should reject header without Bearer prefix")
        void shouldRejectNonBearerHeader() {
            MockServerWebExchange exchange = exchangeFor(PROTECTED_PATH, "Basic dXNlcjpwYXNz");

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(JwtAuthenticationException.class);

            verify(chain, never()).filter(any());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Valid token — proceed with enriched headers")
    class ValidToken {

        @Test @DisplayName("should forward X-User-ID, X-Tenant-ID, X-User-Role downstream")
        void shouldEnrichDownstreamHeaders() {
            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, "Bearer " + validToken());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ArgumentCaptor<ServerWebExchange> captor =
                    ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());

            var headers = captor.getValue().getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-ID")).isEqualTo("user-42");
            assertThat(headers.getFirst("X-Tenant-ID")).isEqualTo("tenant-001");
            assertThat(headers.getFirst("X-User-Role")).isEqualTo("USER");
        }

        @Test @DisplayName("should default X-Tenant-ID to anonymous when claim absent")
        void shouldDefaultTenantToAnonymous() {
            String noTenantToken = Jwts.builder()
                    .subject("user-7")
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(SIGNING_KEY)
                    .compact();
            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, "Bearer " + noTenantToken);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ArgumentCaptor<ServerWebExchange> captor =
                    ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-Tenant-ID"))
                    .isEqualTo("anonymous");
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Invalid tokens — rejected with typed error codes")
    class InvalidTokens {

        @Test @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            String expired = token("user-42", "tenant-001", "USER",
                    new Date(System.currentTimeMillis() - 60_000));
            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, "Bearer " + expired);

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(JwtAuthenticationException.class)
                    .hasMessageContaining("gateway.auth.expired.token");

            verify(chain, never()).filter(any());
        }

        @Test @DisplayName("should reject token signed with a different key")
        void shouldRejectWrongSignature() {
            SecretKey otherKey = Keys.hmacShaKeyFor(
                    "another-secret-key-32-bytes-long".getBytes());
            String forged = Jwts.builder()
                    .subject("attacker")
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(otherKey)
                    .compact();
            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, "Bearer " + forged);

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(JwtAuthenticationException.class)
                    .hasMessageContaining("gateway.auth.signature.invalid");

            verify(chain, never()).filter(any());
        }

        @Test @DisplayName("should reject malformed token")
        void shouldRejectMalformedToken() {
            MockServerWebExchange exchange =
                    exchangeFor(PROTECTED_PATH, "Bearer not.a.jwt");

            assertThatThrownBy(() -> filter.filter(exchange, chain))
                    .isInstanceOf(JwtAuthenticationException.class);

            verify(chain, never()).filter(any());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Filter chain ordering")
    class FilterOrdering {

        @Test @DisplayName("should run at HIGHEST_PRECEDENCE + 10")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder())
                    .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
        }

        @Test @DisplayName("LoadSheddingFilter must not share an order with TenantValidationFilter")
        void loadSheddingOrderIsUnique() {
            LoadSheddingFilter loadShedding = new LoadSheddingFilter(messageSource, 5000);
            assertThat(loadShedding.getOrder())
                    .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 30)
                    .isNotEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
        }
    }
}
