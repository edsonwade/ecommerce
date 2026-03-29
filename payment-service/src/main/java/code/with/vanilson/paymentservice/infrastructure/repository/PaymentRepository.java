package code.with.vanilson.paymentservice.infrastructure.repository;

import code.with.vanilson.paymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PaymentRepository — Infrastructure Layer
 * <p>
 * JPA repository for the Payment domain entity.
 * Interface Segregation (SOLID-I): only exposes what is actually needed.
 * <p>
 * findByIdempotencyKey: used by PaymentService to detect duplicate requests
 * before attempting a new payment — the core of the idempotency guard.
 * findByOrderReference: used for querying payment status by order reference.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    /**
     * Finds a payment by its idempotency key.
     * Returns the existing payment if one already exists for this key,
     * allowing PaymentService to return the same result without a double charge.
     *
     * @param idempotencyKey the unique key derived from orderReference
     * @return Optional containing the existing Payment, or empty if first occurrence
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds a payment by order reference.
     * Used for payment status queries from order-service.
     *
     * @param orderReference the order reference string
     * @return Optional containing the Payment if found
     */
    Optional<Payment> findByOrderReference(String orderReference);
}
