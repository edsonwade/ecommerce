package code.with.vanilson.orderservice.internal;

import code.with.vanilson.tenantcontext.internal.InternalTokenAuthenticationFilter;
import code.with.vanilson.tenantcontext.internal.InternalTokenAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InternalTokenFilterTest — unit tests for the F7 service-to-service shared-secret filter.
 * <p>
 * Proves the trust-boundary contract in isolation: only {@code /internal/**} is guarded, a valid
 * {@code X-Internal-Token} passes through, and any missing/blank/mismatched token — or a blank
 * configured secret (fail-closed) — is rejected with 401 and never forwarded down the chain.
 *
 * @author vamuhong
 * @version 1.0
 */
@DisplayName("InternalTokenFilter — S2S shared-secret guard (unit)")
class InternalTokenFilterTest {

    private static final String CONFIGURED = "s3cr3t-internal-token";
    private static final String INTERNAL_PATH = "/api/v1/orders/internal/purchases/exists";

    private MessageSource messageSource;
    private ObjectMapper objectMapper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        messageSource = mock(MessageSource.class);
        objectMapper = new ObjectMapper();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private InternalTokenFilter filterWithSecret(String secret) {
        return new InternalTokenFilter(secret, messageSource, objectMapper);
    }

    @Nested
    @DisplayName("path scoping")
    class PathScoping {

        @Test
        @DisplayName("skips (shouldNotFilter=true) any non-internal path")
        void skipsNonInternalPath() {
            when(request.getRequestURI()).thenReturn("/api/v1/orders/42");
            assertThat(filterWithSecret(CONFIGURED).shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("guards (shouldNotFilter=false) the /internal path")
        void guardsInternalPath() {
            when(request.getRequestURI()).thenReturn(INTERNAL_PATH);
            assertThat(filterWithSecret(CONFIGURED).shouldNotFilter(request)).isFalse();
        }
    }

    @Nested
    @DisplayName("token validation on /internal")
    class TokenValidation {

        @BeforeEach
        void internalPath() {
            when(request.getRequestURI()).thenReturn(INTERNAL_PATH);
        }

        @Test
        @DisplayName("valid token → forwards down the chain, no 401")
        void validTokenPassesThrough() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(CONFIGURED);

            filterWithSecret(CONFIGURED).doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
            verify(response, never()).setStatus(401);
        }

        @Test
        @DisplayName("missing token → 401, chain not invoked")
        void missingTokenRejected() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(null);

            filterWithSecret(CONFIGURED).doFilter(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(401);
            assertThat(responseBody.toString()).contains("order.internal.token.invalid");
        }

        @Test
        @DisplayName("wrong token → 401, chain not invoked")
        void wrongTokenRejected() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn("not-the-token");

            filterWithSecret(CONFIGURED).doFilter(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("blank configured secret → fail-closed 401 even when a token is presented")
        void blankConfiguredSecretFailsClosed() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn("anything");

            filterWithSecret("").doFilter(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(401);
        }
    }

    /**
     * Hardening pass: the filter is now an adapter over the shared
     * {@link InternalTokenAuthenticator}. These tests prove the two capabilities that were missing —
     * rotation and caller identity — reach the wire through this filter, not just the core.
     */
    @Nested
    @DisplayName("per-caller secrets and rotation (shared authenticator)")
    class PerCallerAndRotation {

        private static final String CURRENT = "current-secret";
        private static final String PREVIOUS = "previous-secret";

        @BeforeEach
        void internalPath() {
            when(request.getRequestURI()).thenReturn(INTERNAL_PATH);
        }

        private InternalTokenFilter filterAccepting(String... tokensForProductService) {
            InternalTokenAuthenticator authenticator = new InternalTokenAuthenticator(
                    Map.of("product-service", List.of(tokensForProductService)), "", false);
            return new InternalTokenFilter(authenticator, messageSource, objectMapper);
        }

        @Test
        @DisplayName("named caller with its own secret → forwarded, and the caller is published for auditing")
        void namedCallerPassesThrough() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(CURRENT);
            when(request.getHeader(InternalTokenAuthenticationFilter.CALLER_HEADER))
                    .thenReturn("product-service");

            filterAccepting(CURRENT).doFilter(request, response, chain);

            verify(chain, times(1)).doFilter(request, response);
            verify(request).setAttribute(
                    InternalTokenAuthenticationFilter.CALLER_ATTRIBUTE, "product-service");
        }

        @Test
        @DisplayName("during rotation BOTH the new and the outgoing secret are accepted")
        void bothSecretsAcceptedDuringRotation() throws Exception {
            InternalTokenFilter filter = filterAccepting(CURRENT, PREVIOUS);

            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(PREVIOUS);
            filter.doFilter(request, response, chain);

            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(CURRENT);
            filter.doFilter(request, response, chain);

            verify(chain, times(2)).doFilter(request, response);
            verify(response, never()).setStatus(401);
        }

        @Test
        @DisplayName("once the outgoing secret is dropped from config it stops working")
        void retiredSecretRejectedAfterRotation() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(PREVIOUS);

            filterAccepting(CURRENT).doFilter(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("a valid secret presented under someone else's name is rejected as impersonation")
        void impersonationRejected() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn(CURRENT);
            when(request.getHeader(InternalTokenAuthenticationFilter.CALLER_HEADER))
                    .thenReturn("cart-service");

            filterAccepting(CURRENT).doFilter(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("the 401 body never echoes the presented secret")
        void rejectionNeverLeaksTheSecret() throws Exception {
            when(request.getHeader(InternalTokenFilter.INTERNAL_TOKEN_HEADER)).thenReturn("attacker-guess");

            filterAccepting(CURRENT).doFilter(request, response, chain);

            assertThat(responseBody.toString())
                    .contains("order.internal.token.invalid")
                    .doesNotContain("attacker-guess")
                    .doesNotContain(CURRENT);
        }
    }
}
