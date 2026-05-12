package com.bvb.transactionmodule.service.DTO.response;

import com.bvb.transactionmodule.domain.TransactionRollback;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class RollbackResponse {

    private UUID rollbackId;
    private UUID originalTransactionId;
    private UUID reversalTransactionId;
    private TransactionRollback.Status rollbackStatus;
    private String reason;
    private String message;
    private Instant createdAt;
    private Instant resolvedAt;

    public static RollbackResponse from(TransactionRollback r, String message) {
        return RollbackResponse.builder()
                .rollbackId(r.getId())
                .originalTransactionId(r.getOriginalTransactionId())
                .reversalTransactionId(r.getReversalTransactionId())
                .rollbackStatus(r.getStatus())
                .reason(r.getReason())
                .message(message)
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .build();
    }
}
