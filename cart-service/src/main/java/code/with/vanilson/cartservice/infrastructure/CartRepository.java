package code.with.vanilson.cartservice.infrastructure;

import code.with.vanilson.cartservice.domain.Cart;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * CartRepository — Infrastructure Layer (Redis)
 * <p>
 * Spring Data Redis CrudRepository.
 * Stores Cart objects as Redis Hashes under key: "cart:{cartId}".
 * TTL is managed by @TimeToLive on the Cart entity (24h sliding window).
 * <p>
 * Interface Segregation (SOLID-I): only exposes what the application layer needs.
 * findByCustomerId: uses the @Indexed customerId secondary index.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface CartRepository extends CrudRepository<Cart, String> {

    /**
     * Finds a cart by the customer's ID.
     * Uses Redis secondary index on customerId field (@Indexed on Cart.customerId).
     *
     * @param customerId the customer's unique identifier
     * @return Optional cart if found, empty if no active cart exists
     */
    Optional<Cart> findByCustomerId(String customerId);
}
