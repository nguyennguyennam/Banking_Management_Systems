package com.bvb.transactionmodule.service.DTO.request;

import com.bvb.transactionmodule.domain.Transaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class TransactionRequest {

    @NotNull
    private UUID sourceAccountId;

    private UUID targetAccountId;     // bắt buộc khi TRANSFER

    @NotNull
    private Transaction.Type transactionType;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount > 0")
    private BigDecimal amount;

    private String location;

    /**
     * Client-generated UUID per transaction attempt; used for deduplication:
     *  - In-flight → idempotency lock returns 409
     *  - Already done → TRANSACTION.idempotency_key UNIQUE returns existing tx
     */
    @NotNull
    private UUID idempotencyKey;

    // ── Programmatic factory (used by recurring job dispatcher) ────────────────

    public static TransactionRequest of(UUID sourceAccountId, UUID targetAccountId,
                                        Transaction.Type type, BigDecimal amount) {
        TransactionRequest req = new TransactionRequest();
        req.sourceAccountId  = sourceAccountId;
        req.targetAccountId  = targetAccountId;
        req.transactionType  = type;
        req.amount           = amount;
        req.idempotencyKey   = UUID.randomUUID();
        return req;
    }
}