package com.bvb.transactionmodule.event;

import com.bvb.transactionmodule.domain.Transaction;

public record RollbackFailedEvent(Transaction transaction, String initiatedBy, String errorDetail) {}
