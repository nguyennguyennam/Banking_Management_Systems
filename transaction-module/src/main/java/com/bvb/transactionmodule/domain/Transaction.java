package com.bvb.transactionmodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Transaction {

    public enum Type   { DEPOSIT, WITHDRAWAL, TRANSFER }
    public enum Status { PENDING, COMPLETED, FAILED, ROLLED_BACK }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id")
    private UUID targetAccountId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "transaction_type::text", write = "?::transaction_type_")
    @Column(name = "transaction_type", nullable = false, columnDefinition = "TRANSACTION_TYPE_")
    private Type transactionType;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "transaction_status::text", write = "?::transaction_status_")
    @Column(name = "transaction_status", nullable = false, columnDefinition = "TRANSACTION_STATUS_")
    private Status transactionStatus;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "transaction_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal transactionFee = BigDecimal.ZERO;

    @Column(name = "location")
    private String location;

    @Column(name = "executed_at")
    private Instant executedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── State transitions ──────────────────────────────────────────────────────

    public void markCompleted(BigDecimal fee) {
        this.transactionStatus = Status.COMPLETED;
        this.transactionFee    = fee;
        this.executedAt        = Instant.now();
    }

    public void markFailed() {
        this.transactionStatus = Status.FAILED;
    }

    public void markRolledBack() {
        this.transactionStatus = Status.ROLLED_BACK;
    }
}
