package com.bvb.transactionmodule.service.usecase;

import com.bvb.sharedmodule.event.DepositRequestedEvent;
import com.bvb.sharedmodule.event.TransferRequestedEvent;
import com.bvb.sharedmodule.event.WithdrawalRequestedEvent;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.event.TransactionCompletedEvent;
import com.bvb.transactionmodule.service.DTO.request.TransactionRequest;
import com.bvb.transactionmodule.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns atomic state transitions for Transaction.
 * Each method runs in its own REQUIRES_NEW transaction so the state
 * change is committed to the DB before the caller proceeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionStateService {

    private final TransactionRepository     transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction savePending(UUID id, TransactionRequest req) {
        Transaction tx = Transaction.builder()
                .id(id)
                .sourceAccountId(req.getSourceAccountId())
                .targetAccountId(req.getTargetAccountId())
                .idempotencyKey(req.getIdempotencyKey())
                .transactionType(req.getTransactionType())
                .amount(req.getAmount())
                .transactionFee(BigDecimal.ZERO)
                .transactionStatus(Transaction.Status.PENDING)
                .location(req.getLocation())
                .build();
        Transaction saved = transactionRepository.save(tx);
        log.info("[TX_STATE] PENDING id={}", id);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishDepositRequested(UUID txId, UUID accountId, BigDecimal amount, String requestedBy) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        eventPublisher.publishEvent(new DepositRequestedEvent(txId, accountId, amount, requestedBy));
        log.info("[TX_STATE] DEPOSIT_REQUESTED id={}", txId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishWithdrawalRequested(UUID txId, UUID accountId, BigDecimal amount, String requestedBy) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        eventPublisher.publishEvent(new WithdrawalRequestedEvent(txId, accountId, amount, requestedBy));
        log.info("[TX_STATE] WITHDRAWAL_REQUESTED id={}", txId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishTransferRequested(UUID txId, UUID sourceId, UUID targetId,
                                         BigDecimal amount, String requestedBy) {
        Objects.requireNonNull(sourceId, "accountId must not be null");
        Objects.requireNonNull(targetId, "targetAccountId must not be null");
        eventPublisher.publishEvent(new TransferRequestedEvent(txId, sourceId, targetId, amount, requestedBy));
        log.info("[TX_STATE] TRANSFER_REQUESTED id={}", txId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction markCompleted(UUID id, BigDecimal fee, String performedBy) {
        int updated = transactionRepository.markCompleted(id, fee);
        if (updated == 0) {
            log.warn("[TX_STATE] markCompleted skipped — tx={} already left PENDING state", id);
            return transactionRepository.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Transaction disappeared: " + id));
        }
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Transaction disappeared: " + id));
        eventPublisher.publishEvent(new TransactionCompletedEvent(tx, performedBy));
        log.info("[TX_STATE] COMPLETED id={} fee={}", id, fee);
        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id) {
        transactionRepository.markFailed(id);
        log.info("[TX_STATE] FAILED id={}", id);
    }
}
