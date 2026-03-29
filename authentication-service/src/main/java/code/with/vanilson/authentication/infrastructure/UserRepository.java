package code.with.vanilson.authentication.infrastructure;

import code.with.vanilson.authentication.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository — Infrastructure Layer
 * <p>
 * Interface Segregation (SOLID-I): only exposes what the auth domain needs.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.accountEnabled = true")
    Optional<User> findActiveUserByEmail(@Param("email") String email);
}
