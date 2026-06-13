package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import code.with.vanilson.orderservice.OrderRepository;
import code.with.vanilson.orderservice.exception.OrderForbiddenException;
import code.with.vanilson.orderservice.exception.OrderNotFoundException;
import code.with.vanilson.tenantcontext.TenantContext;
import code.with.vanilson.tenantcontext.TenantHibernateFilterActivator;
import code.with.vanilson.tenantcontext.security.SecurityPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderLineService — Application Layer (Phase 4 update)
 * <p>
 * Phase 4: sets tenantId from TenantContext on every new order line.
 * <p>
 * Authorization: {@link #findAllByOrderId(Integer)} enforces owner-or-ADMIN access,
 * mirroring {@link code.with.vanilson.orderservice.OrderService#findById(Integer)}.
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
    private final OrderRepository orderRepository;
    private final TenantHibernateFilterActivator filterActivator;
    private final MessageSource messageSource;

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
     * Returns all order lines for a given order ID, after verifying the caller
     * owns the parent order (or is an ADMIN).
     *
     * @param orderId the parent order ID
     * @return list of OrderLineResponse DTOs
     * @throws OrderNotFoundException  if no order exists with the given id
     * @throws OrderForbiddenException if the caller is neither the owner nor an ADMIN
     */
    public List<OrderLineResponse> findAllByOrderId(Integer orderId) {
        filterActivator.activateFilter();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(
                        msg("order.not.found", orderId), "order.not.found"));

        SecurityPrincipal principal = currentPrincipal();
        if (principal != null && !"ADMIN".equals(principal.role())
                && !order.getCustomerId().equals(String.valueOf(principal.userId()))) {
            throw new OrderForbiddenException(
                    msg("order.access.denied"), "order.access.denied");
        }

        return repository.findAllOrderById(orderId)
                .stream()
                .map(mapper::toOrderLineResponse)
                .collect(Collectors.toList());
    }

    private SecurityPrincipal currentPrincipal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityPrincipal sp)) {
            return null;
        }
        return sp;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
