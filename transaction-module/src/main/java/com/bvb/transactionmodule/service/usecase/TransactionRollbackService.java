package com.bvb.transactionmodule.service.usecase;

import com.bvb.transactionmodule.config.exception.RollbackExhaustedException;
import com.bvb.transactionmodule.config.exception.TransactionNotFoundException;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.domain.TransactionRollback;
import com.bvb.transactionmodule.repository.TransactionRepository;
import com.bvb.transactionmodule.repository.TransactionRollbackRepository;
import com.bvb.transactionmodule.service.DTO.response.RollbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionRollbackService {

    private static final int MAX_ATTEMPTS = TransactionRollback.MAX_ROLLBACK_ATTEMPTS;

    private final TransactionRepository        transactionRepository;
    private final TransactionRollbackRepository rollbackRepository;
    private final ReversalExecutor             reversalExecutor;

    @Transactional
    public RollbackResponse rollback(UUID transactionId, String reason, String initiatedBy) {
        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (transaction.getTransactionStatus() != Transaction.Status.COMPLETED) {
            throw new IllegalStateException(
                    "Only COMPLETED transactions can be rolled back. Current status: "
                            + transaction.getTransactionStatus());
        }

        if (rollbackRepository.hasCompleted(transactionId)) {
            throw new IllegalStateException(
                    "Transaction [" + transactionId + "] has already been rolled back.");
        }

        int failedAttempts = rollbackRepository.countByOriginalTransactionId(transactionId);
        if (failedAttempts >= MAX_ATTEMPTS) {
            throw new RollbackExhaustedException(transactionId, failedAttempts);
        }

        return reversalExecutor.execute(transaction, reason, initiatedBy, failedAttempts);
    }
}
