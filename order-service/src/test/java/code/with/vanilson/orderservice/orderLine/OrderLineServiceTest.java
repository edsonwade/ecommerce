package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.orderservice.product.ProductClient;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderLineServiceTest — Unit Tests
 * <p>
 * Covers saveOrderLine() and findAllByOrderId(), including owner-or-ADMIN
 * authorization. Regression for the live-demo bug where a customer viewing
 * their own order received 403 (controller was {@code hasRole('ADMIN')}).
 * Framework: JUnit 5 + Mockito + AssertJ.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderLineService — Unit Tests")
class OrderLineServiceTest {

    @Mock private OrderLineRepository repository;
    @Mock private OrderLineMapper     mapper;
    @Mock private OrderRepository      orderRepository;
    @Mock private TenantHibernateFilterActivator filterActivator;
    @Mock private MessageSource        messageSource;
    @Mock private ProductClient        productClient;

    @InjectMocks
    private OrderLineService orderLineService;

    private static final Integer ORDER_ID = 42;
    private static final String  OWNER_ID = "7";

    private OrderLineRequest request;
    private OrderLine        orderLine;
    private OrderLineResponse response;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId("test-tenant-123");

        lenient().when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        request = new OrderLineRequest(null, ORDER_ID, 1, 2.0);

        orderLine = OrderLine.builder()
                .id(10)
                .order(Order.builder().orderId(ORDER_ID).build())
                .productId(1)
                .quantity(2.0)
                .build();

        response = new OrderLineResponse(10, 1, 2.0);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal("user@test.com", userId, "test-tenant-123", role),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Order orderOwnedBy(String customerId) {
        return Order.builder().orderId(ORDER_ID).customerId(customerId).build();
    }

    // -------------------------------------------------------
    // saveOrderLine
    // -------------------------------------------------------
    @Nested
    @DisplayName("saveOrderLine")
    class SaveOrderLine {

        @Test
        @DisplayName("should persist order line and return generated ID")
        void shouldSaveAndReturnId() {
            when(mapper.toOrderLine(request)).thenReturn(orderLine);
            when(repository.save(any(OrderLine.class))).thenReturn(orderLine);

            Integer result = orderLineService.saveOrderLine(request);

            assertThat(result).isEqualTo(10);
            verify(repository, times(1)).save(any(OrderLine.class));
        }

        @Test
        @DisplayName("should set tenantId from TenantContext before saving")
        void shouldSetTenantIdBeforeSave() {
            when(mapper.toOrderLine(request)).thenReturn(orderLine);
            when(repository.save(any(OrderLine.class))).thenReturn(orderLine);

            orderLineService.saveOrderLine(request);

            ArgumentCaptor<OrderLine> captor = ArgumentCaptor.forClass(OrderLine.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getTenantId()).isEqualTo("test-tenant-123");
        }
    }

    // -------------------------------------------------------
    // findAllByOrderId — owner-or-ADMIN authorization
    // -------------------------------------------------------
    @Nested
    @DisplayName("findAllByOrderId")
    class FindAllByOrderId {

        @Test
        @DisplayName("owner: should return mapped list of order line responses")
        void ownerGetsMappedResponses() {
            authenticateAs(7L, "USER");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOwnedBy(OWNER_ID)));
            when(repository.findAllOrderById(ORDER_ID)).thenReturn(List.of(orderLine));
            when(mapper.toOrderLineResponse(orderLine)).thenReturn(response);

            List<OrderLineResponse> result = orderLineService.findAllByOrderId(ORDER_ID);

            assertThat(result)
                    .isNotNull()
                    .hasSize(1)
                    .first()
                    .satisfies(r -> {
                        assertThat(r.id()).isEqualTo(10);
                        assertThat(r.productId()).isEqualTo(1);
                        assertThat(r.quantity()).isEqualTo(2.0);
                    });
            verify(filterActivator).activateFilter();
        }

        @Test
        @DisplayName("owner: should return empty list when no order lines exist")
        void ownerGetsEmptyList() {
            authenticateAs(7L, "USER");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOwnedBy(OWNER_ID)));
            when(repository.findAllOrderById(ORDER_ID)).thenReturn(List.of());

            List<OrderLineResponse> result = orderLineService.findAllByOrderId(ORDER_ID);

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("ADMIN: may read lines for an order they do not own")
        void adminGetsLines() {
            authenticateAs(999L, "ADMIN");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOwnedBy(OWNER_ID)));
            when(repository.findAllOrderById(ORDER_ID)).thenReturn(List.of());

            assertThat(orderLineService.findAllByOrderId(ORDER_ID)).isEmpty();
        }

        @Test
        @DisplayName("non-owner: should throw OrderForbiddenException (403)")
        void nonOwnerForbidden() {
            authenticateAs(8L, "USER");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOwnedBy(OWNER_ID)));

            assertThatThrownBy(() -> orderLineService.findAllByOrderId(ORDER_ID))
                    .isInstanceOf(OrderForbiddenException.class);

            verify(repository, never()).findAllOrderById(any());
        }

        @Test
        @DisplayName("seller: receives ONLY their own lines, never the full order (no cross-seller leak)")
        void sellerGetsOnlyOwnLines() {
            authenticateAs(55L, "SELLER");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOwnedBy(OWNER_ID)));

            OrderLine sellerLine = OrderLine.builder()
                    .id(11)
                    .order(Order.builder().orderId(ORDER_ID).build())
                    .productId(9)
                    .quantity(1.0)
                    .sellerId("55")
                    .build();
            when(repository.findByOrderIdAndSellerId(ORDER_ID, "55")).thenReturn(List.of(sellerLine));
            when(mapper.toOrderLineResponse(sellerLine)).thenReturn(new OrderLineResponse(11, 9, 1.0));

            List<OrderLineResponse> result = orderLineService.findAllByOrderId(ORDER_ID);

            assertThat(result).hasSize(1)
                    .first()
                    .satisfies(r -> assertThat(r.productId()).isEqualTo(9));
            // The seller path must NOT read the whole order — that is the leak being fixed.
            verify(repository, never()).findAllOrderById(any());
        }

        @Test
        @DisplayName("seller owning no line in the order: 403 (cannot view a basket they didn't sell into)")
        void sellerWithNoOwnLinesForbidden() {
            authenticateAs(77L, "SELLER");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(orderOwnedBy(OWNER_ID)));
            when(repository.findByOrderIdAndSellerId(ORDER_ID, "77")).thenReturn(List.of());

            assertThatThrownBy(() -> orderLineService.findAllByOrderId(ORDER_ID))
                    .isInstanceOf(OrderForbiddenException.class);

            verify(repository, never()).findAllOrderById(any());
        }

        @Test
        @DisplayName("missing order: should throw OrderNotFoundException (404)")
        void missingOrder() {
            authenticateAs(7L, "USER");
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderLineService.findAllByOrderId(ORDER_ID))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }
}
