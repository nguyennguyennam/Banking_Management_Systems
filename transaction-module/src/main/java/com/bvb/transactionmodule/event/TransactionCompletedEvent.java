package com.bvb.transactionmodule.event;

import com.bvb.transactionmodule.domain.Transaction;

public record TransactionCompletedEvent(Transaction transaction, String performedBy) {}
