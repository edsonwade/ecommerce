package code.with.vanilson.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Token — Domain Entity
 * <p>
 * Stores issued JWT tokens to support:
 * 1. Logout (mark token as revoked)
 * 2. Token rotation (expire old tokens on refresh)
 * 3. Security audit trail
 * <p>
 * Design: the gateway validates JWTs cryptographically (signature + expiry).
 * This DB table is checked only on logout and refresh — NOT on every request.
 * Trade-off: stateless JWT validation at the gateway for performance;
 * stateful revocation check only when needed.
 * </p>
 *
 * @author vamuhong
 * @version 2.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "token")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String tokenValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TokenType tokenType = TokenType.BEARER;

    @Column(nullable = false)
    @Builder.Default
    private boolean expired = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isValid() {
        return !expired && !revoked;
    }

    // -------------------------------------------------------
    public enum TokenType {
        BEARER,
        REFRESH
    }
}
