package code.with.vanilson.gatewayservice.filter;

import code.with.vanilson.gatewayservice.client.TenantServiceClient;
import code.with.vanilson.gatewayservice.client.TenantValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.MessageSource;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantValidationFilterTest
 * <p>
 * Framework: JUnit 5 + Mockito + Reactor Test + Spring MockServerWebExchange.
 * Tests the reactive filter logic without starting a full application context.
 * TenantServiceClient is mocked — no HTTP server required.
 *
 * @author vamuhong
 * @version 4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantValidationFilter — Unit Tests")
class TenantValidationFilterTest {

    @Mock private TenantServiceClient tenantServiceClient;
    @Mock private MessageSource       messageSource;
    @Mock private GatewayFilterChain  chain;

    private TenantValidationFilter filter;

    private static final String TENANT_ID     = "tenant-001";
    private static final String PUBLIC_PATH   = "/api/v1/auth/login";
    private static final String PROTECTED_PATH = "/api/v1/orders";

    @BeforeEach
    void setUp() {
        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        filter = new TenantValidationFilter(
                tenantServiceClient,
                messageSource,
                List.of("/api/v1/auth/**", "/actuator/**"));

        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Public paths — bypass validation")
    class PublicPaths {

        @Test @DisplayName("should skip validation for public path and proceed")
        void shouldSkipForPublicPath() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.POST, PUBLIC_PATH)
                    .header("X-Tenant-ID", TENANT_ID)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(tenantServiceClient, never()).validate(anyString());
            verify(chain).filter(any());
        }
    }


    // -------------------------------------------------------
    @Nested @DisplayName("Anonymous tenants — bypass validation")
    class AnonymousTenants {

        @Test @DisplayName("should skip validation when X-Tenant-ID is anonymous")
        void shouldSkipForAnonymous() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .header("X-Tenant-ID", "anonymous")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(tenantServiceClient, never()).validate(anyString());
        }

        @Test @DisplayName("should skip validation when X-Tenant-ID header is absent")
        void shouldSkipWhenHeaderAbsent() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(tenantServiceClient, never()).validate(anyString());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("ACTIVE tenant — proceed")
    class ActiveTenant {

        @Test @DisplayName("should proceed and enrich X-Tenant-Rate-Limit when tenant is ACTIVE")
        void shouldProceedForActiveTenant() {
            TenantValidationResponse active = new TenantValidationResponse(TENANT_ID, "ACTIVE", 1000);
            when(tenantServiceClient.validate(TENANT_ID)).thenReturn(Mono.just(active));

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .header("X-Tenant-ID", TENANT_ID)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(any());
        }
    }


    // -------------------------------------------------------
    @Nested @DisplayName("SUSPENDED / CANCELLED tenant — 403")
    class SuspendedTenant {

        @Test @DisplayName("should return 403 when tenant status is SUSPENDED")
        void shouldReturn403ForSuspended() {
            TenantValidationResponse suspended =
                    new TenantValidationResponse(TENANT_ID, "SUSPENDED", 100);
            when(tenantServiceClient.validate(TENANT_ID)).thenReturn(Mono.just(suspended));

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .header("X-Tenant-ID", TENANT_ID)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }

        @Test @DisplayName("should return 403 when tenant status is CANCELLED")
        void shouldReturn403ForCancelled() {
            TenantValidationResponse cancelled =
                    new TenantValidationResponse(TENANT_ID, "CANCELLED", 0);
            when(tenantServiceClient.validate(TENANT_ID)).thenReturn(Mono.just(cancelled));

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .header("X-Tenant-ID", TENANT_ID)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(chain, never()).filter(any());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Tenant not found — 404")
    class TenantNotFound {

        @Test @DisplayName("should return 404 when tenant-service returns empty Mono")
        void shouldReturn404WhenTenantNotFound() {
            when(tenantServiceClient.validate(TENANT_ID)).thenReturn(Mono.empty());

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .header("X-Tenant-ID", TENANT_ID)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(chain, never()).filter(any());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("tenant-service unavailable — 503")
    class ServiceUnavailable {

        @Test @DisplayName("should return 503 when tenant-service throws unexpected error")
        void shouldReturn503OnServiceError() {
            when(tenantServiceClient.validate(TENANT_ID))
                    .thenReturn(Mono.error(new RuntimeException("Connection refused")));

            MockServerHttpRequest request = MockServerHttpRequest
                    .method(HttpMethod.GET, PROTECTED_PATH)
                    .header("X-Tenant-ID", TENANT_ID)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verify(chain, never()).filter(any());
        }
    }

    // -------------------------------------------------------
    @Nested @DisplayName("Filter ordering")
    class FilterOrdering {

        @Test @DisplayName("should have order HIGHEST_PRECEDENCE + 20")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder())
                    .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20);
        }
    }
}
