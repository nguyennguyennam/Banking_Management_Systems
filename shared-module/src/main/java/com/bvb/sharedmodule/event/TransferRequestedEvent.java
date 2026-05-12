package com.bvb.sharedmodule.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequestedEvent(
        UUID transactionId,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String requestedBy
) {}
