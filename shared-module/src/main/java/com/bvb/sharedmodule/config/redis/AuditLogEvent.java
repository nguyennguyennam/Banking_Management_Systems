package com.bvb.sharedmodule.config.redis;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonDeserialize(builder = AuditLogEvent.AuditLogEventBuilder.class)
public class AuditLogEvent {

    public enum EntityType { ACCOUNT, TRANSACTION, CUSTOMER }

    public enum Action {
        CREATE,
        UPDATE,
        DELETE,

        ACCOUNT_ACTIVE,
        ACCOUNT_LOCKED,
        ACCOUNT_CLOSED,

        DEPOSIT,
        WITHDRAWAL,
        TRANSFER,
        TRANSACTION_ROLLED_BACK
    }

    private UUID       entityId;
    private UUID       accountId;     // source account for TRANSACTION events; null for ACCOUNT events
    private EntityType entityType;
    private Action     action;
    private String     oldValue;
    private String     newValue;
    private String     changedBy;
    private String     changedByRole;
    private String     reason;
    private String     ipAddress;
    private Instant    occurredAt;

    // Empty declaration — Lombok fills in the builder body.
    // @JsonPOJOBuilder tells Jackson to use this builder for deserialization,
    // with no method prefix (Lombok generates entityId(), action(), etc., not withEntityId()).
    @JsonPOJOBuilder(withPrefix = "")
    public static class AuditLogEventBuilder {}
}
