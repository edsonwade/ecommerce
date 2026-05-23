package code.with.vanilson.orderservice.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CustomerSnapshotRepository — JPA repository for the CQRS customer read model.
 * <p>
 * Used by {@link code.with.vanilson.orderservice.OrderService} via {@code resolveCustomer()}
 * and populated by {@link CustomerEventConsumer}.
 * </p>
 */
@Repository
public interface CustomerSnapshotRepository extends JpaRepository<CustomerSnapshot, String> {
}
