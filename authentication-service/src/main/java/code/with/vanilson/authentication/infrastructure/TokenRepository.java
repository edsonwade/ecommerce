package code.with.vanilson.authentication.infrastructure;

import code.with.vanilson.authentication.domain.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TokenRepository — Infrastructure Layer
 * <p>
 * Manages JWT token persistence for logout + refresh workflows.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByTokenValue(String tokenValue);

    /** Returns all valid (non-expired, non-revoked) tokens for a user. */
    @Query("""
           SELECT t FROM Token t
           WHERE t.user.id = :userId
             AND t.expired = false
             AND t.revoked  = false
           """)
    List<Token> findAllValidTokensByUser(@Param("userId") Long userId);

    /** Bulk-revoke all active tokens for a user on logout or password change. */
    @Modifying
    @Query("""
           UPDATE Token t
           SET t.expired = true, t.revoked = true
           WHERE t.user.id = :userId
             AND (t.expired = false OR t.revoked = false)
           """)
    void revokeAllUserTokens(@Param("userId") Long userId);
}
