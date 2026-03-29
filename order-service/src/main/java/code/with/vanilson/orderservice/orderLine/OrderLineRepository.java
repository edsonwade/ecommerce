package code.with.vanilson.orderservice.orderLine;

import org.springframework.data.jpa.repository.JpaRepository;
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
     *
     * @param orderId the ID of the parent order
     * @return list of OrderLine entities for the given orderId
     */
    List<OrderLine> findAllOrderById(Integer orderId);
}
