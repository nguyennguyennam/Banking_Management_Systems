package com.bvb.transactionmodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction_rollback")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransactionRollback {

    public enum Status { PENDING, COMPLETED, FAILED }

    public static final int MAX_ROLLBACK_ATTEMPTS = 3;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "original_transaction_id", nullable = false, updatable = false)
    private UUID originalTransactionId;

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "status::text", write = "?::rollback_status_")
    @Column(name = "status", nullable = false, columnDefinition = "ROLLBACK_STATUS_")
    private Status status;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Column(name = "initiated_by", length = 100, nullable = false)
    private String initiatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static TransactionRollback completed(UUID originalTransactionId,
                                                String reason, String initiatedBy) {
        return TransactionRollback.builder()
                .id(UUID.randomUUID())
                .originalTransactionId(originalTransactionId)
                .status(Status.COMPLETED)
                .reason(reason)
                .initiatedBy(initiatedBy)
                .resolvedAt(Instant.now())
                .build();
    }

    public static TransactionRollback failed(UUID originalTransactionId,
                                             String reason, String initiatedBy) {
        return TransactionRollback.builder()
                .id(UUID.randomUUID())
                .originalTransactionId(originalTransactionId)
                .status(Status.FAILED)
                .reason(reason)
                .initiatedBy(initiatedBy)
                .resolvedAt(Instant.now())
                .build();
    }
}
