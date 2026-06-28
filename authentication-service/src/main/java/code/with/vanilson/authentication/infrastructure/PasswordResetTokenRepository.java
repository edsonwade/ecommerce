package code.with.vanilson.authentication.infrastructure;

import code.with.vanilson.authentication.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PasswordResetTokenRepository — Infrastructure Layer.
 * <p>
 * Lookups are always by {@code tokenHash} (the SHA-256 of the raw token) — the raw token is
 * never stored. Interface Segregation (SOLID-I): only what the reset flow needs.
 * </p>
 *
 * @author vamuhong
 * @version 1.0
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates every still-usable token for a user by marking it consumed. Called before
     * issuing a new token so a user only ever has one live reset link at a time.
     */
    @Modifying
    @Query("""
           UPDATE PasswordResetToken t
           SET t.usedAt = :now
           WHERE t.user.id = :userId
             AND t.usedAt IS NULL
           """)
    void invalidateActiveTokensForUser(@Param("userId") Long userId,
                                       @Param("now") LocalDateTime now);
}
