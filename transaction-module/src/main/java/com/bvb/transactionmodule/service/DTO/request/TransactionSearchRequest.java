package com.bvb.transactionmodule.service.DTO.request;

import com.bvb.transactionmodule.domain.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TransactionSearchRequest {

    /** Filter by source account (optional). */
    private final UUID sourceAccountId;

    /** Filter by transaction type (optional). */
    private final Transaction.Type type;

    /** Filter by transaction status (optional). */
    private final Transaction.Status status;

    /** Amount range — both null means no filter. */
    private final BigDecimal amountMin;
    private final BigDecimal amountMax;

    /** Execution timestamp range (executed_at). */
    private final Instant executedFrom;
    private final Instant executedTo;

    /** Creation timestamp range (created_at). */
    private final Instant createdFrom;
    private final Instant createdTo;
}
