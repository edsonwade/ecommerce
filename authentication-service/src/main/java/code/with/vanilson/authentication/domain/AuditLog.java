package code.with.vanilson.authentication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog — records every ADMIN-initiated role promotion or demotion.
 * Written on every successful {@code UserManagementService.updateRole()} call.
 */
@Entity
@Table(name = "role_audit_log")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "target_user_id", nullable = false, length = 255)
    private String targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", nullable = false, length = 50)
    private Role previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false, length = 50)
    private Role newRole;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
