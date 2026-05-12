package com.bvb.transactionmodule.config.exception;

import lombok.Getter;

@Getter
public class RollbackExhaustedException extends RuntimeException {

    private final java.util.UUID transactionId;
    private final int attempts;

    public RollbackExhaustedException(java.util.UUID transactionId, int attempts) {
        super(String.format(
                "Transaction [%s] could not be rolled back after %d attempt(s). " +
                        "Please contact support.", transactionId, attempts));
        this.transactionId = transactionId;
        this.attempts = attempts;
    }

}