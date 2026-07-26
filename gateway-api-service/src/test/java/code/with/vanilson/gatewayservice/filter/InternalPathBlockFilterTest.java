package code.with.vanilson.gatewayservice.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * InternalPathBlockFilterTest
 * <p>
 * Framework: JUnit 5 + Mockito + Reactor Test + Spring MockServerWebExchange, mirroring
 * {@link TenantValidationFilterTest} — reactive filter logic with no application context.
 * <p>
 * The contract under test is a security boundary, so the negative cases matter as much as the
 * positive ones: everything under an {@code /internal} segment must die at the edge, and everything
 * else must pass through untouched.
 *
 * @author vamuhong
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalPathBlockFilter — Unit Tests")
class InternalPathBlockFilterTest {

    @Mock private MessageSource messageSource;
    @Mock private GatewayFilterChain chain;

    private InternalPathBlockFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        filter = new InternalPathBlockFilter(
                messageSource,
                List.of("/api/v1/*/internal", "/api/v1/*/internal/**"));

        lenient().when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private MockServerWebExchange exchangeFor(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Internal paths — terminated at the edge")
    class BlockedPaths {

        @ParameterizedTest(name = "{0} is blocked")
        @ValueSource(strings = {
                "/api/v1/orders/internal/purchases/exists",
                "/api/v1/orders/internal",
                "/api/v1/customers/internal",
                "/api/v1/customers/internal/42",
                "/api/v1/products/internal/anything/deeper"
        })
        @DisplayName("should answer 404 and never call the chain")
        void shouldBlockInternalPaths(String path) {
            MockServerWebExchange exchange = exchangeFor(HttpMethod.GET, path);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("should block regardless of HTTP method — a POST is no more welcome than a GET")
        void shouldBlockEveryMethod() {
            for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
                    HttpMethod.PATCH, HttpMethod.DELETE)) {
                MockServerWebExchange exchange =
                        exchangeFor(method, "/api/v1/orders/internal/purchases/exists");

                StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

                assertThat(exchange.getResponse().getStatusCode())
                        .as("method %s", method)
                        .isEqualTo(HttpStatus.NOT_FOUND);
            }
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("should return a body indistinguishable from an ordinary 404 — no existence leak")
        void shouldNotLeakThatTheEndpointExists() {
            MockServerWebExchange exchange =
                    exchangeFor(HttpMethod.GET, "/api/v1/orders/internal/purchases/exists");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            String body = exchange.getResponse().getBodyAsString()
                    .block();
            assertThat(body)
                    .contains("\"status\":404")
                    .contains("gateway.path.not.found")
                    // Nothing may hint at what was really behind the path.
                    .doesNotContain("internal")
                    .doesNotContain("X-Internal-Token")
                    .doesNotContain("forbidden");
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Public paths — untouched")
    class AllowedPaths {

        @ParameterizedTest(name = "{0} passes through")
        @ValueSource(strings = {
                "/api/v1/orders",
                "/api/v1/orders/102",
                "/api/v1/orders/102/status",
                "/api/v1/products",
                "/api/v1/products/6702/reviews",
                "/api/v1/products/reviews/admin",
                "/api/v1/customers/9",
                "/actuator/health"
        })
        @DisplayName("should forward normal traffic")
        void shouldAllowNormalPaths(String path) {
            MockServerWebExchange exchange = exchangeFor(HttpMethod.GET, path);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("should NOT block a path that merely starts with the word — ant match, not contains()")
        void shouldNotBlockOnSubstring() {
            // The trap a naive path.contains("/internal") implementation falls into.
            MockServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/api/v1/orders/internally");

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(any());
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Ordering — must win before anything else runs")
    class Ordering {

        @Test
        @DisplayName("should sit between RequestIdFilter (+0) and JwtAuthenticationFilter (+10)")
        void shouldRunBeforeAuthentication() {
            assertThat(filter.getOrder())
                    .isGreaterThan(Ordered.HIGHEST_PRECEDENCE)
                    .isLessThan(Ordered.HIGHEST_PRECEDENCE + 10);
        }
    }

    // -------------------------------------------------------
    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("an empty pattern list blocks nothing — the filter is inert, never accidentally closed")
        void shouldBeInertWithoutPatterns() {
            InternalPathBlockFilter inert = new InternalPathBlockFilter(messageSource, List.of());
            MockServerWebExchange exchange =
                    exchangeFor(HttpMethod.GET, "/api/v1/orders/internal/purchases/exists");

            StepVerifier.create(inert.filter(exchange, chain)).verifyComplete();

            verify(chain).filter(any());
        }

        @Test
        @DisplayName("custom patterns are honoured, so a new internal surface needs config, not code")
        void shouldHonourCustomPatterns() {
            InternalPathBlockFilter custom =
                    new InternalPathBlockFilter(messageSource, List.of("/api/v1/secret/**"));

            MockServerWebExchange blocked = exchangeFor(HttpMethod.GET, "/api/v1/secret/thing");
            StepVerifier.create(custom.filter(blocked, chain)).verifyComplete();
            assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            MockServerWebExchange allowed =
                    exchangeFor(HttpMethod.GET, "/api/v1/orders/internal/purchases/exists");
            StepVerifier.create(custom.filter(allowed, chain)).verifyComplete();
            assertThat(allowed.getResponse().getStatusCode()).isNull();
        }
    }

}
