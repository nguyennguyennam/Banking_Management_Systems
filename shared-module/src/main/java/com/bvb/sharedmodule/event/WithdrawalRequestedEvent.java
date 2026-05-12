package com.bvb.sharedmodule.event;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawalRequestedEvent(
        UUID transactionId,
        UUID accountId,
        BigDecimal amount,
        String requestedBy
) {}
