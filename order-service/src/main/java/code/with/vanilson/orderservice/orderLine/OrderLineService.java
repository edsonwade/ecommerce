package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.tenantcontext.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderLineService — Application Layer (Phase 4 update)
 * <p>
 * Phase 4: sets tenantId from TenantContext on every new order line.
 * </p>
 *
 * @author vamuhong
 * @version 4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLineService {

    private final OrderLineRepository repository;
    private final OrderLineMapper mapper;

    /**
     * Persists a single order line.
     * Called by OrderService.saveOrderWithLines() within its @Transactional boundary.
     *
     * @param request the order line data
     * @return the generated ID of the saved order line
     */
    public Integer saveOrderLine(OrderLineRequest request) {
        OrderLine orderLine = mapper.toOrderLine(request);
        orderLine.setTenantId(TenantContext.requireCurrentTenantId());
        Integer savedId = repository.save(orderLine).getId();
        log.debug("[OrderLineService] Saved order line: id=[{}] orderId=[{}] productId=[{}] qty=[{}]",
                savedId, request.orderId(), request.productId(), request.quantity());
        return savedId;
    }

    /**
     * Returns all order lines for a given order ID.
     *
     * @param orderId the parent order ID
     * @return list of OrderLineResponse DTOs
     */
    public List<OrderLineResponse> findAllByOrderId(Integer orderId) {
        return repository.findAllOrderById(orderId)
                .stream()
                .map(mapper::toOrderLineResponse)
                .collect(Collectors.toList());
    }
}
