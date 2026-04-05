package code.with.vanilson.authentication.infrastructure;

import code.with.vanilson.authentication.domain.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TokenRepository — Infrastructure Layer
 * <p>
 * Manages JWT token persistence for logout + refresh workflows.
 * All lookups use JTI (UUID) — never the full JWT string.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    /** Look up a token by its JTI (replaces the old findByTokenValue). */
    Optional<Token> findByJti(String jti);

    /** Returns all valid (non-expired, non-revoked) tokens for a user. */
    @Query("""
           SELECT t FROM Token t
           WHERE t.user.id = :userId
             AND t.expired = false
             AND t.revoked  = false
           """)
    List<Token> findAllValidTokensByUser(@Param("userId") Long userId);

    /**
     * Looks up a valid (non-expired, non-revoked) token by JTI and type.
     * Used by the refresh flow to verify the DB state before issuing a new pair.
     */
    @Query("""
           SELECT t FROM Token t
           WHERE t.jti       = :jti
             AND t.tokenType = :type
             AND t.expired   = false
             AND t.revoked   = false
           """)
    Optional<Token> findValidTokenByJtiAndType(@Param("jti") String jti,
                                               @Param("type") Token.TokenType type);

    /** Bulk-revoke all active tokens for a user on logout or password change. */
    @Modifying
    @Query("""
           UPDATE Token t
           SET t.expired = true, t.revoked = true
           WHERE t.user.id = :userId
             AND (t.expired = false OR t.revoked = false)
           """)
    void revokeAllUserTokens(@Param("userId") Long userId);

    /** Deletes tokens that are both expired/revoked AND older than the given cutoff. */
    @Modifying
    @Query("""
           DELETE FROM Token t
           WHERE (t.expired = true OR t.revoked = true)
             AND t.createdAt < :cutoff
           """)
    void deleteExpiredTokensBefore(@Param("cutoff") LocalDateTime cutoff);
}
