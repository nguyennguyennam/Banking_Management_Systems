package com.bvb.transactionmodule.event;

import com.bvb.transactionmodule.domain.Transaction;

public record RollbackSuccessEvent(Transaction transaction, String initiatedBy) {}
