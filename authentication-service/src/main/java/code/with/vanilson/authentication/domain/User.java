package code.with.vanilson.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;
import java.time.LocalDateTime;
import java.util.List;

/**
 * User — Domain Entity (pure JPA aggregate root)
 * <p>
 * Maps to the app_user table (reserved word 'user' avoided).
 * <p>
 * Clean Architecture: this entity is the aggregate root of the auth domain.
 * Spring Security coupling is handled by {@link UserDetailsAdapter} (SRP — SOLID-S).
 * <p>
 * tenantId: every user belongs to a tenant — critical for SaaS multi-tenancy.
 * The JWT filter reads this claim and propagates it as X-Tenant-ID header.
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
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String firstname;

    @Column(nullable = false, length = 100)
    private String lastname;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Role role = Role.USER;

    /**
     * Tenant identifier — propagated as JWT claim "tenantId".
     * Gateway reads this and adds X-Tenant-ID header to all downstream requests.
     */
    @Column(nullable = false, length = 100)
    @Builder.Default
    private String tenantId = "default";

    @Column(nullable = false)
    @Builder.Default
    private boolean accountLocked = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean accountEnabled = true;

    @OneToMany(mappedBy = "user")
    private List<Token> tokens;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstname + " " + lastname;
    }
}
