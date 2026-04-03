package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.tenantcontext.TenantContext;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderLineServiceTest — Unit Tests
 * <p>
 * Covers saveOrderLine() and findAllByOrderId().
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

    @InjectMocks
    private OrderLineService orderLineService;

    private OrderLineRequest request;
    private OrderLine        orderLine;
    private OrderLineResponse response;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId("test-tenant-123");

        request = new OrderLineRequest(null, 42, 1, 2.0);

        orderLine = OrderLine.builder()
                .id(10)
                .order(Order.builder().orderId(42).build())
                .productId(1)
                .quantity(2.0)
                .build();

        response = new OrderLineResponse(10, 2.0);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
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
    // findAllByOrderId
    // -------------------------------------------------------
    @Nested
    @DisplayName("findAllByOrderId")
    class FindAllByOrderId {

        @Test
        @DisplayName("should return mapped list of order line responses")
        void shouldReturnMappedResponses() {
            when(repository.findAllOrderById(42)).thenReturn(List.of(orderLine));
            when(mapper.toOrderLineResponse(orderLine)).thenReturn(response);

            List<OrderLineResponse> result = orderLineService.findAllByOrderId(42);

            assertThat(result)
                    .isNotNull()
                    .hasSize(1)
                    .first()
                    .satisfies(r -> {
                        assertThat(r.id()).isEqualTo(10);
                        assertThat(r.quantity()).isEqualTo(2.0);
                    });
        }

        @Test
        @DisplayName("should return empty list when no order lines exist")
        void shouldReturnEmptyList() {
            when(repository.findAllOrderById(99)).thenReturn(List.of());

            List<OrderLineResponse> result = orderLineService.findAllByOrderId(99);

            assertThat(result).isNotNull().isEmpty();
        }
    }
}
