package com.bvb.transactionmodule.service.DTO.response;

import com.bvb.transactionmodule.domain.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID sourceAccountId;
    private UUID targetAccountId;
    private Transaction.Type transactionType;
    private Transaction.Status transactionStatus;
    private BigDecimal amount;
    private BigDecimal transactionFee;
    private String location;
    private Instant executedAt;
    private Instant createdAt;

    public static TransactionResponse from(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .sourceAccountId(t.getSourceAccountId())
                .targetAccountId(t.getTargetAccountId())
                .transactionType(t.getTransactionType())
                .transactionStatus(t.getTransactionStatus())
                .amount(t.getAmount())
                .transactionFee(t.getTransactionFee())
                .location(t.getLocation())
                .executedAt(t.getExecutedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}