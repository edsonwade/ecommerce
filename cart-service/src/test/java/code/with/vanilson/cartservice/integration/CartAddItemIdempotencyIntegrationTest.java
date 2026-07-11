package code.with.vanilson.cartservice.integration;

import code.with.vanilson.cartservice.domain.Cart;
import code.with.vanilson.cartservice.infrastructure.CartRepository;
import code.with.vanilson.tenantcontext.security.JwtAuthenticationFilter;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CartAddItemIdempotencyIntegrationTest — B4 (idempotency as a platform convention)
 * <p>
 * Full-context proof that a retried POST /items with the same {@code Idempotency-Key}
 * does not increment the quantity twice: real security filter chain, real tenant
 * interceptor, real CartController → CartService → Cart domain logic over HTTP.
 * <p>
 * Redis I/O is the ONLY stubbed seam: {@link CartRepository} is replaced by a
 * map-backed stub because on this host the Lettuce client hangs inside the test
 * JVM (same reason every integration test in this module mocks it — see
 * {@link CartPrometheusScrapeIntegrationTest}). The map preserves state across
 * requests, so the second delivery reads exactly the cart the first delivery
 * persisted — the precondition the replay guard checks.
 * JwtAuthenticationFilter is a pass-through mock and the authentication is
 * injected per-request (same recipe as CartControllerSecurityTest); JWT parsing
 * is not what this test proves.
 * </p>
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "application.security.jwt.secret-key=dGVzdFNlY3JldEtleUZvclRlc3RpbmdPbmx5Tm90Rm9yUHJvZHVjdGlvblVzYWdlMDAwMDAwMDAwMDAwMDAwMA==",
        "management.health.redis.enabled=false"
})
@DisplayName("Cart addItem idempotency — cart-service (integration, B4)")
class CartAddItemIdempotencyIntegrationTest {

    @Autowired
    WebApplicationContext context;

    @MockBean
    CartRepository cartRepository;

    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockMvc mockMvc;

    /** Map standing in for Redis — state survives across requests within a test. */
    private final Map<String, Cart> redisStore = new ConcurrentHashMap<>();

    private static final String TENANT_HDR = "X-Tenant-ID";
    private static final String TENANT_VAL = "test-tenant-123";
    private static final String CUSTOMER   = "42";
    private static final String CART_KEY   = "cart:" + TENANT_VAL + ":" + CUSTOMER;
    private static final String BODY =
            "{\"productId\":1,\"productName\":\"Laptop\",\"productDescription\":\"Gaming\","
                    + "\"unitPrice\":1200.0,\"quantity\":2.0,\"availableQuantity\":10}";

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").header(TENANT_HDR, TENANT_VAL))
                .build();

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        redisStore.clear();
        when(cartRepository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(redisStore.get(inv.<String>getArgument(0))));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart cart = inv.getArgument(0);
            redisStore.put(cart.getCartId(), cart);
            return cart;
        });
    }

    private static UsernamePasswordAuthenticationToken userAuth() {
        return new UsernamePasswordAuthenticationToken(
                new SecurityPrincipal("user@test.com", 42L, TENANT_VAL, "USER"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void postAddItem(String idempotencyKey, double expectedQuantity) throws Exception {
        var request = post("/api/v1/carts/{customerId}/items", CUSTOMER)
                .header(TENANT_HDR, TENANT_VAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .with(authentication(userAuth()));
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity", is(expectedQuantity)));
    }

    @Nested
    @DisplayName("POST /items with Idempotency-Key")
    class WithIdempotencyKey {

        @Test
        @DisplayName("retrying the same add with the same key does not double the quantity")
        void sameKeyTwice_quantityAppliedOnce() throws Exception {
            postAddItem("idem-int-001", 2.0);
            // Retry (e.g. after a false 503 on a write that succeeded) — same key
            postAddItem("idem-int-001", 2.0);

            Cart persisted = redisStore.get(CART_KEY);
            assertThat(persisted).isNotNull();
            assertThat(persisted.getItems()).hasSize(1);
            assertThat(persisted.getItems().get(0).getQuantity())
                    .as("A replayed Idempotency-Key must not increment the quantity again")
                    .isEqualTo(2.0);
            assertThat(persisted.hasIdempotencyKey("idem-int-001")).isTrue();
        }

        @Test
        @DisplayName("adds with different keys are two distinct user actions and both apply")
        void differentKeys_bothApply() throws Exception {
            postAddItem("idem-int-A", 2.0);
            postAddItem("idem-int-B", 4.0);

            Cart persisted = redisStore.get(CART_KEY);
            assertThat(persisted.getItems().get(0).getQuantity()).isEqualTo(4.0);
        }
    }

    @Nested
    @DisplayName("POST /items without Idempotency-Key")
    class WithoutIdempotencyKey {

        @Test
        @DisplayName("legacy behaviour preserved — repeated adds merge quantities")
        void noKey_repeatedAddsMerge() throws Exception {
            postAddItem(null, 2.0);
            postAddItem(null, 4.0);

            Cart persisted = redisStore.get(CART_KEY);
            assertThat(persisted.getItems().get(0).getQuantity()).isEqualTo(4.0);
        }
    }
}
