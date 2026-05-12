package com.bvb.auditmodule.domain;

import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuditLog {

    public enum RoleEnum { ADMIN, USER }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "entity_type::text", write = "?::audit_entity_")
    @Column(name = "entity_type", nullable = false, columnDefinition = "AUDIT_ENTITY_")
    private AuditLogEvent.EntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "action::text", write = "?::audit_action_")
    @Column(nullable = false, columnDefinition = "AUDIT_ACTION_")
    private AuditLogEvent.Action action;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "changed_by_role::text", write = "?::user_role_")
    @Column(name = "changed_by_role", nullable = false, columnDefinition = "USER_ROLE_")
    private RoleEnum changedByRole;

    @Column(length = 500)
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public static AuditLog from(AuditLogEvent event) {
        return AuditLog.builder()
                .id(UUID.randomUUID())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .accountId(event.getAccountId())
                .action(event.getAction())
                .oldValue(event.getOldValue())
                .newValue(event.getNewValue())
                .changedBy(event.getChangedBy())
                .changedByRole(parseRole(event.getChangedByRole()))
                .reason(event.getReason())
                .ipAddress(event.getIpAddress())
                .build();
    }

    private static RoleEnum parseRole(String role) {
        try {
            return RoleEnum.valueOf(role);
        } catch (Exception e) {
            return RoleEnum.USER;
        }
    }
}
