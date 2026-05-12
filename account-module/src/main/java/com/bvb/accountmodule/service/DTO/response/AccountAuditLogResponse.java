package com.bvb.accountmodule.service.DTO.response;

import com.bvb.auditmodule.domain.AuditLog;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AccountAuditLogResponse {

    private UUID                     id;
    private AuditLogEvent.EntityType entityType;
    private UUID                     entityId;
    private UUID                     accountId;
    private AuditLogEvent.Action     action;
    private String                   changedBy;
    private String                   reason;
    private Instant                  changedAt;

    public static AccountAuditLogResponse from(AuditLog a) {
        return AccountAuditLogResponse.builder()
                .id(a.getId())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .accountId(a.getAccountId())
                .action(a.getAction())
                .changedBy(a.getChangedBy())
                .reason(a.getReason())
                .changedAt(a.getChangedAt())
                .build();
    }
}
