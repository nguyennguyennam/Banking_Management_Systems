package com.bvb.accountmodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.bvb.sharedmodule.exception.InsufficientBalanceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Account {

    public enum AccountType { PAYMENT }
    public enum Status      { ACTIVE, LOCKED, CLOSED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_number", unique = true, nullable = false, length = 10)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "account_type::text", write = "?::account_type_")
    @Column(name = "account_type", nullable = false, columnDefinition = "ACCOUNT_TYPE_")
    private AccountType accountType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "transaction_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal transactionLimit;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "status::text", write = "?::account_status_")
    @Column(nullable = false, columnDefinition = "ACCOUNT_STATUS_")
    private Status status;

    @CreatedDate
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Business methods ───────────────────────────────────────────────────────

    public void lock()   { this.status = Status.LOCKED; }
    public void unlock() { this.status = Status.ACTIVE; }
    public void close()  { this.status = Status.CLOSED; }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (amount.compareTo(transactionLimit) > 0)
            throw new InsufficientBalanceException("Transaction amount exceeds the allowed limit");
        if (amount.compareTo(balance) > 0)
            throw new InsufficientBalanceException("Insufficient balance");
        if (amount <= 0) {
            throw new InsufficientBalanceException("Amount must be greater than zero");
        }
        this.balance = this.balance.subtract(amount);
    }

    public boolean isActive() { return status == Status.ACTIVE; }
}