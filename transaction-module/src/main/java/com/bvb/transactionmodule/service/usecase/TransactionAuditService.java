package com.bvb.transactionmodule.service.usecase;

import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import com.bvb.sharedmodule.config.redis.AuditLogPublisher;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.event.RollbackFailedEvent;
import com.bvb.transactionmodule.event.RollbackSuccessEvent;
import com.bvb.transactionmodule.event.TransactionCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionAuditService {

    private final AuditLogPublisher auditLogPublisher;
    private final ObjectMapper      objectMapper;

    // ── Event listeners (fire after the enclosing DB transaction commits) ─────

    @Async("auditTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @CacheEvict(value = "transaction", key = "#event.transaction().id", beforeInvocation = true)
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        Transaction tx = event.transaction();
        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(tx.getId())
                .accountId(tx.getSourceAccountId())
                .entityType(AuditLogEvent.EntityType.TRANSACTION)
                .action(resolveAction(tx.getTransactionType()))
                .newValue(toJson(tx))
                .changedBy(event.performedBy())
                .reason("Transaction completed successfully")
                .occurredAt(Instant.now())
                .build());
    }

    @Async("auditTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRollbackSuccess(RollbackSuccessEvent event) {
        Transaction tx = event.transaction();
        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(tx.getId())
                .accountId(tx.getSourceAccountId())
                .entityType(AuditLogEvent.EntityType.TRANSACTION)
                .action(AuditLogEvent.Action.TRANSACTION_ROLLED_BACK)
                .oldValue(toJson(tx))
                .changedBy(event.initiatedBy())
                .reason("Transaction rolled back successfully")
                .occurredAt(Instant.now())
                .build());
    }

    @Async("auditTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRollbackFailed(RollbackFailedEvent event) {
        Transaction tx = event.transaction();
        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(tx.getId())
                .accountId(tx.getSourceAccountId())
                .entityType(AuditLogEvent.EntityType.TRANSACTION)
                .action(AuditLogEvent.Action.TRANSACTION_ROLLED_BACK)
                .oldValue(toJson(tx))
                .changedBy(event.initiatedBy())
                .reason("Rollback permanently failed: " + event.errorDetail())
                .occurredAt(Instant.now())
                .build());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private AuditLogEvent.Action resolveAction(Transaction.Type type) {
        return switch (type) {
            case DEPOSIT    -> AuditLogEvent.Action.DEPOSIT;
            case WITHDRAWAL -> AuditLogEvent.Action.WITHDRAWAL;
            case TRANSFER   -> AuditLogEvent.Action.TRANSFER;
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
