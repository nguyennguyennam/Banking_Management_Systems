package com.bvb.sharedmodule.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResultEvent(
        UUID       transactionId,
        UUID       accountId,       // source account for all transaction types; never null
        boolean    success,
        BigDecimal fee,
        String     failureReason,
        String     performedBy
) {}
