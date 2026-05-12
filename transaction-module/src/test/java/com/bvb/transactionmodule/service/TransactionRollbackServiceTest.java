package com.bvb.transactionmodule.service;

import com.bvb.transactionmodule.config.exception.RollbackExhaustedException;
import com.bvb.transactionmodule.config.exception.TransactionNotFoundException;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.domain.TransactionRollback;
import com.bvb.transactionmodule.repository.TransactionRepository;
import com.bvb.transactionmodule.repository.TransactionRollbackRepository;
import com.bvb.transactionmodule.service.DTO.response.RollbackResponse;
import com.bvb.transactionmodule.service.usecase.ReversalExecutor;
import com.bvb.transactionmodule.service.usecase.TransactionRollbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionRollbackService")
class TransactionRollbackServiceTest {

    @Mock TransactionRepository        transactionRepository;
    @Mock TransactionRollbackRepository rollbackRepository;
    @Mock ReversalExecutor             reversalExecutor;

    @InjectMocks TransactionRollbackService service;

    private UUID        txId;
    private Transaction completedTx;

    @BeforeEach
    void setUp() {
        txId        = UUID.randomUUID();
        completedTx = buildTx(txId, Transaction.Status.COMPLETED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED transaction: delegates to ReversalExecutor")
    void rollback_completedTransaction_delegatesToReversalExecutor() {
        RollbackResponse expectedResponse = buildRollbackResponse();

        when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(completedTx));
        when(rollbackRepository.hasCompleted(txId)).thenReturn(false);
        when(rollbackRepository.countByOriginalTransactionId(txId)).thenReturn(0);
        when(reversalExecutor.execute(eq(completedTx), eq("fraud"), eq("admin"), eq(0)))
                .thenReturn(expectedResponse);

        RollbackResponse response = service.rollback(txId, "fraud", "admin");

        assertThat(response).isSameAs(expectedResponse);
        verify(reversalExecutor).execute(completedTx, "fraud", "admin", 0);
    }

    @Test
    @DisplayName("first retry (1 previous attempt): passes failedAttempts=1 to executor")
    void rollback_withPreviousAttempt_passesCorrectCount() {
        when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(completedTx));
        when(rollbackRepository.hasCompleted(txId)).thenReturn(false);
        when(rollbackRepository.countByOriginalTransactionId(txId)).thenReturn(1);
        when(reversalExecutor.execute(eq(completedTx), any(), any(), eq(1)))
                .thenReturn(buildRollbackResponse());

        service.rollback(txId, "reason", "admin");

        verify(reversalExecutor).execute(completedTx, "reason", "admin", 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Guard conditions — status check
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("status guard")
    class StatusGuard {

        @Test
        @DisplayName("PENDING transaction throws IllegalStateException")
        void pendingTransaction_throwsIllegalState() {
            Transaction pending = buildTx(txId, Transaction.Status.PENDING);
            when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.rollback(txId, "reason", "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED");

            verifyNoInteractions(reversalExecutor);
        }

        @Test
        @DisplayName("FAILED transaction throws IllegalStateException")
        void failedTransaction_throwsIllegalState() {
            Transaction failed = buildTx(txId, Transaction.Status.FAILED);
            when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(failed));

            assertThatThrownBy(() -> service.rollback(txId, "reason", "admin"))
                    .isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(reversalExecutor);
        }

        @Test
        @DisplayName("ROLLED_BACK transaction throws IllegalStateException")
        void rolledBackTransaction_throwsIllegalState() {
            Transaction rolledBack = buildTx(txId, Transaction.Status.ROLLED_BACK);
            when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(rolledBack));

            assertThatThrownBy(() -> service.rollback(txId, "reason", "admin"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Guard conditions — not found / already rolled back / exhausted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("non-existent transaction throws TransactionNotFoundException")
    void transactionNotFound_throwsTransactionNotFoundException() {
        when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollback(txId, "reason", "admin"))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    @DisplayName("already completed rollback throws IllegalStateException")
    void alreadyRolledBack_throwsIllegalStateException() {
        when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(completedTx));
        when(rollbackRepository.hasCompleted(txId)).thenReturn(true);

        assertThatThrownBy(() -> service.rollback(txId, "reason", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rollback");

        verifyNoInteractions(reversalExecutor);
    }

    @Test
    @DisplayName("max attempts already reached throws RollbackExhaustedException")
    void maxAttemptsReached_throwsRollbackExhaustedException() {
        when(transactionRepository.findByIdWithLock(txId)).thenReturn(Optional.of(completedTx));
        when(rollbackRepository.hasCompleted(txId)).thenReturn(false);
        when(rollbackRepository.countByOriginalTransactionId(txId))
                .thenReturn(TransactionRollback.MAX_ROLLBACK_ATTEMPTS);

        assertThatThrownBy(() -> service.rollback(txId, "reason", "admin"))
                .isInstanceOf(RollbackExhaustedException.class);

        verifyNoInteractions(reversalExecutor);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Transaction buildTx(UUID id, Transaction.Status status) {
        return Transaction.builder()
                .id(id)
                .sourceAccountId(UUID.randomUUID())
                .idempotencyKey(UUID.randomUUID())
                .transactionType(Transaction.Type.DEPOSIT)
                .transactionStatus(status)
                .amount(new BigDecimal("100.00"))
                .transactionFee(BigDecimal.ZERO)
                .build();
    }

    private RollbackResponse buildRollbackResponse() {
        TransactionRollback record = TransactionRollback.completed(txId, "fraud", "admin");
        return RollbackResponse.from(record, "Transaction rolled back successfully.");
    }
}
