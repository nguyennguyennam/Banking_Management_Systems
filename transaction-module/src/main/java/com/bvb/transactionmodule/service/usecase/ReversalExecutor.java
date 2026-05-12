package com.bvb.transactionmodule.service.usecase;

import com.bvb.accountmodule.service.usecase.AccountTransactionService;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.domain.TransactionRollback;
import com.bvb.transactionmodule.event.RollbackFailedEvent;
import com.bvb.transactionmodule.event.RollbackSuccessEvent;
import com.bvb.transactionmodule.repository.TransactionRepository;
import com.bvb.transactionmodule.repository.TransactionRollbackRepository;
import com.bvb.transactionmodule.service.DTO.response.RollbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalExecutor {

    private final TransactionRepository        transactionRepository;
    private final TransactionRollbackRepository rollbackRepository;
    private final AccountTransactionService    accountService;
    private final RollbackPersistenceService   rollbackPersistenceService;
    private final ApplicationEventPublisher    eventPublisher;

    @Transactional
    public RollbackResponse execute(Transaction transaction,
                                    String reason,
                                    String initiatedBy,
                                    int previousAttempts) {
        UUID transactionId   = transaction.getId();
        int  currentAttempt  = previousAttempts + 1;

        log.info("[REVERSAL] START id={} attempt={}/{} by={}",
                transactionId, currentAttempt, TransactionRollback.MAX_ROLLBACK_ATTEMPTS, initiatedBy);

        try {
            switch (transaction.getTransactionType()) {
                case DEPOSIT    -> accountService.reverseDeposit(
                        transaction.getSourceAccountId(), transaction.getAmount(), transactionId);
                case WITHDRAWAL -> accountService.reverseWithdraw(
                        transaction.getSourceAccountId(), transaction.getAmount(), transactionId);
                case TRANSFER   -> accountService.reverseTransfer(
                        transaction.getSourceAccountId(), transaction.getTargetAccountId(),
                        transaction.getAmount(), transactionId);
            }

            int updated = transactionRepository.markRolledBack(transactionId);
            if (updated == 0) {
                throw new IllegalStateException(
                        "Failed to update ROLLED_BACK status for transaction: " + transactionId);
            }

            TransactionRollback record = rollbackRepository.save(
                    TransactionRollback.completed(transactionId, reason, initiatedBy));

            eventPublisher.publishEvent(new RollbackSuccessEvent(transaction, initiatedBy));

            log.info("[REVERSAL] SUCCESS id={} attempt={}", transactionId, currentAttempt);

            String msg = currentAttempt == 1
                    ? "Transaction rolled back successfully."
                    : "Transaction rolled back successfully (after " + currentAttempt + " attempt(s)).";

            return RollbackResponse.from(record, msg);

        } catch (Exception e) {
            log.error("[REVERSAL] FAILED id={} attempt={} error={}",
                    transactionId, currentAttempt, e.getMessage());

            // Save in a separate transaction so the record survives this tx's rollback.
            rollbackPersistenceService.saveFailed(transactionId, reason, initiatedBy);

            eventPublisher.publishEvent(
                    new RollbackFailedEvent(transaction, initiatedBy, e.getMessage()));

            throw e;
        }
    }
}
