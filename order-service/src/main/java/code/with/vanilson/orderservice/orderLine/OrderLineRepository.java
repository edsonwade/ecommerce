package code.with.vanilson.orderservice.orderLine;

import code.with.vanilson.orderservice.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderLineRepository — Infrastructure Layer
 * <p>
 * JPA repository for OrderLine entity.
 * Follows Interface Segregation (SOLID-I): only exposes what is actually needed.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Integer> {

    /**
     * Returns all order lines belonging to a given order.
     * <p>
     * Filters by the parent order's primary key ({@code OrderLine.order.orderId},
     * column {@code order_id}). An explicit JPQL query is used because a derived
     * method name cannot unambiguously express the nested path: the relationship
     * field is {@code order} and the {@link code.with.vanilson.orderservice.Order}
     * PK field is {@code orderId}. (The previous derived name
     * {@code findAllOrderById} was parsed as {@code WHERE id = :orderId}, i.e. it
     * filtered by the order line's OWN primary key — so it returned no lines.)
     *
     * @param orderId the ID of the parent order
     * @return list of OrderLine entities for the given orderId
     */
    @Query("SELECT ol FROM OrderLine ol WHERE ol.order.orderId = :orderId")
    List<OrderLine> findAllOrderById(@Param("orderId") Integer orderId);

    /**
     * Returns the distinct orders that contain at least one line owned by the given
     * seller (line.sellerId = product.createdBy). Backs the seller order list — each
     * seller sees only the orders placed for their own products.
     *
     * @param sellerId the seller's userId
     * @return distinct parent orders, newest-first
     */
    @Query("SELECT DISTINCT ol.order FROM OrderLine ol WHERE ol.sellerId = :sellerId "
            + "ORDER BY ol.order.orderId DESC")
    List<Order> findDistinctOrdersBySellerId(@Param("sellerId") String sellerId);

    /**
     * @return true if the given seller owns at least one line in the given order —
     *         used to authorise a seller viewing an order's detail / lines.
     */
    @Query("SELECT (COUNT(ol) > 0) FROM OrderLine ol "
            + "WHERE ol.order.orderId = :orderId AND ol.sellerId = :sellerId")
    boolean existsByOrderIdAndSellerId(@Param("orderId") Integer orderId,
                                       @Param("sellerId") String sellerId);
}
