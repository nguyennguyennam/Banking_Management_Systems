package com.bvb.accountmodule.service.DTO.response;

import com.bvb.accountmodule.domain.Account;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AccountResponse {

    private UUID id;
    private UUID customerId;
    private String accountNumber;
    private Account.AccountType accountType;
    private BigDecimal balance;
    private BigDecimal transactionLimit;
    private Account.Status status;
    private Instant openedAt;
    private Instant updatedAt;

    public static AccountResponse toResponse(Account a) {
        return AccountResponse.builder()
                .id(a.getId())
                .customerId(a.getCustomerId())
                .accountNumber(a.getAccountNumber())
                .accountType(a.getAccountType())
                .balance(a.getBalance())
                .transactionLimit(a.getTransactionLimit())
                .status(a.getStatus())
                .openedAt(a.getOpenedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}