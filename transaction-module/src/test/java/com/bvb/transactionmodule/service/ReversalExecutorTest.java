package com.bvb.transactionmodule.service;

import com.bvb.accountmodule.service.usecase.AccountTransactionService;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.domain.TransactionRollback;
import com.bvb.transactionmodule.event.RollbackFailedEvent;
import com.bvb.transactionmodule.event.RollbackSuccessEvent;
import com.bvb.transactionmodule.repository.TransactionRepository;
import com.bvb.transactionmodule.repository.TransactionRollbackRepository;
import com.bvb.transactionmodule.service.DTO.response.RollbackResponse;
import com.bvb.transactionmodule.service.usecase.ReversalExecutor;
import com.bvb.transactionmodule.service.usecase.RollbackPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReversalExecutor")
class ReversalExecutorTest {

    @Mock TransactionRepository        transactionRepository;
    @Mock TransactionRollbackRepository rollbackRepository;
    @Mock AccountTransactionService    accountService;
    @Mock RollbackPersistenceService   rollbackPersistenceService;
    @Mock ApplicationEventPublisher    eventPublisher;

    @InjectMocks ReversalExecutor executor;

    private UUID       txId;
    private UUID       sourceId;
    private UUID       targetId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        txId     = UUID.randomUUID();
        sourceId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        amount   = new BigDecimal("250.00");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deposit reversal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEPOSIT: calls reverseDeposit, marks ROLLED_BACK, saves COMPLETED record, fires event")
    void execute_deposit_revertsSuccessfully() {
        Transaction tx    = buildTx(Transaction.Type.DEPOSIT);
        TransactionRollback saved = TransactionRollback.completed(txId, "fraud", "admin");

        when(transactionRepository.markRolledBack(txId)).thenReturn(1);
        when(rollbackRepository.save(any())).thenReturn(saved);

        RollbackResponse response = executor.execute(tx, "fraud", "admin", 0);

        verify(accountService).reverseDeposit(sourceId, amount, txId);
        verify(transactionRepository).markRolledBack(txId);
        assertThat(response.getRollbackStatus()).isEqualTo(TransactionRollback.Status.COMPLETED);

        ArgumentCaptor<RollbackSuccessEvent> eventCaptor = ArgumentCaptor.forClass(RollbackSuccessEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().initiatedBy()).isEqualTo("admin");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Withdrawal reversal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("WITHDRAWAL: calls reverseWithdraw, marks ROLLED_BACK, fires event")
    void execute_withdrawal_revertsSuccessfully() {
        Transaction tx    = buildTx(Transaction.Type.WITHDRAWAL);
        TransactionRollback saved = TransactionRollback.completed(txId, "error", "admin");

        when(transactionRepository.markRolledBack(txId)).thenReturn(1);
        when(rollbackRepository.save(any())).thenReturn(saved);

        executor.execute(tx, "error", "admin", 0);

        verify(accountService).reverseWithdraw(sourceId, amount, txId);
        verifyNoMoreInteractions(accountService);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transfer reversal
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TRANSFER: calls reverseTransfer with both accounts, marks ROLLED_BACK")
    void execute_transfer_revertsSuccessfully() {
        Transaction tx    = buildTransferTx();
        TransactionRollback saved = TransactionRollback.completed(txId, "error", "admin");

        when(transactionRepository.markRolledBack(txId)).thenReturn(1);
        when(rollbackRepository.save(any())).thenReturn(saved);

        executor.execute(tx, "error", "admin", 0);

        verify(accountService).reverseTransfer(sourceId, targetId, amount, txId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attempt message
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("first attempt: response message is plain (no attempt count)")
    void execute_firstAttempt_messageIsPlain() {
        Transaction tx    = buildTx(Transaction.Type.DEPOSIT);
        TransactionRollback saved = TransactionRollback.completed(txId, "r", "admin");

        when(transactionRepository.markRolledBack(txId)).thenReturn(1);
        when(rollbackRepository.save(any())).thenReturn(saved);

        RollbackResponse response = executor.execute(tx, "r", "admin", 0);

        assertThat(response.getMessage()).doesNotContain("lần thử");
    }

    @Test
    @DisplayName("second attempt: response message mentions attempt count")
    void execute_secondAttempt_messageMentionsAttemptCount() {
        Transaction tx    = buildTx(Transaction.Type.DEPOSIT);
        TransactionRollback saved = TransactionRollback.completed(txId, "r", "admin");

        when(transactionRepository.markRolledBack(txId)).thenReturn(1);
        when(rollbackRepository.save(any())).thenReturn(saved);

        RollbackResponse response = executor.execute(tx, "r", "admin", 1);

        assertThat(response.getMessage()).contains("2 lần thử");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Failure paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        @DisplayName("AccountService fails: persists FAILED record via RollbackPersistenceService and rethrows")
        void accountServiceFails_persistsFailedRecordAndRethrows() {
            Transaction tx = buildTx(Transaction.Type.DEPOSIT);
            doThrow(new RuntimeException("Network error"))
                    .when(accountService).reverseDeposit(any(), any(), any());

            assertThatThrownBy(() -> executor.execute(tx, "reason", "admin", 0))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Network error");

            verify(rollbackPersistenceService).saveFailed(txId, "reason", "admin");
            verify(rollbackRepository, never()).save(any());
        }

        @Test
        @DisplayName("markRolledBack returns 0: persists FAILED record and throws IllegalStateException")
        void markRolledBackReturnsZero_persistsFailedAndThrowsIllegalState() {
            Transaction tx = buildTx(Transaction.Type.DEPOSIT);
            when(transactionRepository.markRolledBack(txId)).thenReturn(0);

            assertThatThrownBy(() -> executor.execute(tx, "reason", "admin", 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ROLLED_BACK");

            verify(rollbackPersistenceService).saveFailed(txId, "reason", "admin");
            verify(rollbackRepository, never()).save(any());
        }

        @Test
        @DisplayName("any failure: fires RollbackFailedEvent")
        void anyFailure_firesRollbackFailedEvent() {
            Transaction tx = buildTx(Transaction.Type.DEPOSIT);
            doThrow(new RuntimeException("Timeout"))
                    .when(accountService).reverseDeposit(any(), any(), any());

            assertThatThrownBy(() -> executor.execute(tx, "reason", "admin", 0))
                    .isInstanceOf(RuntimeException.class);

            ArgumentCaptor<RollbackFailedEvent> captor = ArgumentCaptor.forClass(RollbackFailedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().initiatedBy()).isEqualTo("admin");
            assertThat(captor.getValue().errorDetail()).contains("Timeout");
        }

        @Test
        @DisplayName("final attempt failure: persists FAILED record (same as any attempt)")
        void finalAttemptFailure_persistsFailedRecord() {
            Transaction tx = buildTx(Transaction.Type.DEPOSIT);
            doThrow(new RuntimeException("Timeout"))
                    .when(accountService).reverseDeposit(any(), any(), any());

            assertThatThrownBy(() -> executor.execute(tx, "reason", "admin",
                    TransactionRollback.MAX_ROLLBACK_ATTEMPTS - 1))
                    .isInstanceOf(RuntimeException.class);

            verify(rollbackPersistenceService).saveFailed(txId, "reason", "admin");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Transaction buildTx(Transaction.Type type) {
        return Transaction.builder()
                .id(txId)
                .sourceAccountId(sourceId)
                .idempotencyKey(UUID.randomUUID())
                .transactionType(type)
                .transactionStatus(Transaction.Status.COMPLETED)
                .amount(amount)
                .transactionFee(BigDecimal.ZERO)
                .build();
    }

    private Transaction buildTransferTx() {
        return Transaction.builder()
                .id(txId)
                .sourceAccountId(sourceId)
                .targetAccountId(targetId)
                .idempotencyKey(UUID.randomUUID())
                .transactionType(Transaction.Type.TRANSFER)
                .transactionStatus(Transaction.Status.COMPLETED)
                .amount(amount)
                .transactionFee(BigDecimal.ZERO)
                .build();
    }
}
