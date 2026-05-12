package com.bvb.auditmodule.service.DTO.response;

import com.bvb.auditmodule.domain.AuditLog;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AuditLogResponse {

    private UUID                      id;
    private AuditLogEvent.EntityType  entityType;
    private UUID                      entityId;
    private UUID                      accountId;
    private AuditLogEvent.Action      action;
    private String                    oldValue;
    private String                    newValue;
    private String                    changedBy;
    private String                    changedByRole;
    private String                    reason;
    private String                    ipAddress;
    private Instant                   changedAt;

    public static AuditLogResponse from(AuditLog a) {
        return AuditLogResponse.builder()
                .id(a.getId())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .accountId(a.getAccountId())
                .action(a.getAction())
                .oldValue(a.getOldValue())
                .newValue(a.getNewValue())
                .changedBy(a.getChangedBy())
                .changedByRole(a.getChangedByRole() != null
                        ? a.getChangedByRole().name() : null)
                .reason(a.getReason())
                .ipAddress(a.getIpAddress())
                .changedAt(a.getChangedAt())
                .build();
    }

    public static AuditLogResponse fromForUser(AuditLog a) {
        return AuditLogResponse.builder()
                .id(a.getId())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .accountId(a.getAccountId())
                .action(a.getAction())
                .changedBy(a.getChangedBy())
                .changedByRole(a.getChangedByRole() != null
                        ? a.getChangedByRole().name() : null)
                .reason(a.getReason())
                .changedAt(a.getChangedAt())
                .build();
    }
}