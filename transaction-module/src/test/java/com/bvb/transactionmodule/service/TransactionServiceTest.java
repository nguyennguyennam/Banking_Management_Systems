package com.bvb.transactionmodule.service;

import com.bvb.accountmodule.service.usecase.AccountTransactionService;
import com.bvb.transactionmodule.config.exception.TransactionNotFoundException;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.repository.TransactionRepository;
import com.bvb.transactionmodule.service.DTO.request.TransactionRequest;
import com.bvb.transactionmodule.service.DTO.response.TransactionResponse;
import com.bvb.transactionmodule.service.usecase.TransactionService;
import com.bvb.transactionmodule.service.usecase.TransactionStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService")
class TransactionServiceTest {

    @Mock TransactionRepository        transactionRepository;
    @Mock TransactionStateService      stateService;
    @Mock AccountTransactionService    accountService;

    @InjectMocks TransactionService service;

    private UUID         sourceId;
    private UUID         targetId;
    private UUID         idempotencyKey;
    private UUID         txId;
    private TransactionRequest depositRequest;
    private TransactionRequest withdrawalRequest;
    private TransactionRequest transferRequest;

    @BeforeEach
    void setUp() {
        sourceId       = UUID.randomUUID();
        targetId       = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID();
        txId           = UUID.randomUUID();

        depositRequest    = buildRequest(Transaction.Type.DEPOSIT,    sourceId, null,     idempotencyKey, new BigDecimal("500.00"));
        withdrawalRequest = buildRequest(Transaction.Type.WITHDRAWAL,  sourceId, null,     idempotencyKey, new BigDecimal("200.00"));
        transferRequest   = buildRequest(Transaction.Type.TRANSFER,   sourceId, targetId, idempotencyKey, new BigDecimal("300.00"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createTransaction — happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTransaction")
    class CreateTransaction {

        @Test
        @DisplayName("deposit: saves PENDING then marks COMPLETED after AccountService succeeds")
        void deposit_savePendingThenCompleted() {
            Transaction pendingTx   = buildTx(txId, Transaction.Status.PENDING,   Transaction.Type.DEPOSIT);
            Transaction completedTx = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);

            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(stateService.savePending(any(UUID.class), eq(depositRequest))).thenReturn(pendingTx);
            when(accountService.deposit(eq(sourceId), eq(new BigDecimal("500.00")), any(UUID.class)))
                    .thenReturn(BigDecimal.ZERO);
            when(stateService.markCompleted(any(UUID.class), eq(BigDecimal.ZERO), eq("testuser")))
                    .thenReturn(completedTx);

            TransactionResponse response = service.createTransaction(depositRequest, "testuser");

            assertThat(response.getTransactionStatus()).isEqualTo(Transaction.Status.COMPLETED);
            assertThat(response.getTransactionType()).isEqualTo(Transaction.Type.DEPOSIT);

            verify(stateService).savePending(any(), eq(depositRequest));
            verify(accountService).deposit(eq(sourceId), eq(new BigDecimal("500.00")), any());
            verify(stateService).markCompleted(any(), eq(BigDecimal.ZERO), eq("testuser"));
            verifyNoMoreInteractions(stateService);
        }

        @Test
        @DisplayName("withdrawal: saves PENDING then marks COMPLETED")
        void withdrawal_savePendingThenCompleted() {
            Transaction pending   = buildTx(txId, Transaction.Status.PENDING,   Transaction.Type.WITHDRAWAL);
            Transaction completed = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.WITHDRAWAL);

            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(stateService.savePending(any(), eq(withdrawalRequest))).thenReturn(pending);
            when(accountService.withdraw(eq(sourceId), eq(new BigDecimal("200.00")), any())).thenReturn(BigDecimal.ZERO);
            when(stateService.markCompleted(any(), eq(BigDecimal.ZERO), eq("user1"))).thenReturn(completed);

            TransactionResponse response = service.createTransaction(withdrawalRequest, "user1");

            assertThat(response.getTransactionStatus()).isEqualTo(Transaction.Status.COMPLETED);
            verify(accountService).withdraw(eq(sourceId), eq(new BigDecimal("200.00")), any());
        }

        @Test
        @DisplayName("transfer: saves PENDING then marks COMPLETED")
        void transfer_savePendingThenCompleted() {
            Transaction pending   = buildTx(txId, Transaction.Status.PENDING,   Transaction.Type.TRANSFER);
            Transaction completed = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.TRANSFER);

            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(stateService.savePending(any(), eq(transferRequest))).thenReturn(pending);
            when(accountService.transfer(eq(sourceId), eq(targetId), eq(new BigDecimal("300.00")), any()))
                    .thenReturn(BigDecimal.ZERO);
            when(stateService.markCompleted(any(), eq(BigDecimal.ZERO), eq("user1"))).thenReturn(completed);

            service.createTransaction(transferRequest, "user1");

            verify(accountService).transfer(eq(sourceId), eq(targetId), eq(new BigDecimal("300.00")), any());
        }

        @Test
        @DisplayName("idempotent key: returns existing transaction without calling AccountService")
        void idempotentKey_returnsExisting_noAccountServiceCall() {
            Transaction existing = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);
            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

            TransactionResponse response = service.createTransaction(depositRequest, "user1");

            assertThat(response.getId()).isEqualTo(txId);
            verifyNoInteractions(stateService, accountService);
        }

        @Test
        @DisplayName("AccountService throws: saves FAILED and rethrows exception")
        void accountServiceFails_marksFailedAndRethrows() {
            Transaction pending = buildTx(txId, Transaction.Status.PENDING, Transaction.Type.DEPOSIT);

            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(stateService.savePending(any(), eq(depositRequest))).thenReturn(pending);
            when(accountService.deposit(any(), any(), any()))
                    .thenThrow(new IllegalStateException("Insufficient balance"));

            assertThatThrownBy(() -> service.createTransaction(depositRequest, "user1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient balance");

            verify(stateService).markFailed(any(UUID.class));
        }

        @Test
        @DisplayName("race condition on idempotency key: resolves via repository fallback")
        void raceConditionOnIdempotencyKey_resolvesFromRepository() {
            Transaction existing = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);

            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
            when(stateService.savePending(any(), any()))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));
            when(transactionRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

            TransactionResponse response = service.createTransaction(depositRequest, "user1");

            assertThat(response.getId()).isEqualTo(txId);
            verifyNoInteractions(accountService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // createTransaction — validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTransaction validation")
    class Validation {

        @Test
        @DisplayName("TRANSFER without targetAccountId throws IllegalArgumentException")
        void transfer_missingTargetAccountId_throwsIllegalArgument() {
            TransactionRequest noTarget = buildRequest(Transaction.Type.TRANSFER, sourceId, null,
                    UUID.randomUUID(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.createTransaction(noTarget, "user1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetAccountId");
        }

        @Test
        @DisplayName("TRANSFER with same source and target throws IllegalArgumentException")
        void transfer_sameSourceAndTarget_throwsIllegalArgument() {
            TransactionRequest sameAccounts = buildRequest(Transaction.Type.TRANSFER, sourceId, sourceId,
                    UUID.randomUUID(), new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.createTransaction(sameAccounts, "user1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Source and target accounts must be different");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("existing transaction returns response with correct ID")
        void existingTransaction_returnsResponse() {
            Transaction tx = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

            TransactionResponse response = service.getById(txId);

            assertThat(response.getId()).isEqualTo(txId);
        }

        @Test
        @DisplayName("non-existent transaction throws TransactionNotFoundException")
        void notFound_throwsTransactionNotFoundException() {
            UUID unknownId = UUID.randomUUID();
            when(transactionRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(unknownId))
                    .isInstanceOf(TransactionNotFoundException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getByAccount
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByAccount returns paginated results mapped to responses")
    void getByAccount_returnsMappedPage() {
        Transaction tx = buildTx(txId, Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);
        when(transactionRepository.findAllByAccountId(eq(sourceId), any()))
                .thenReturn(new PageImpl<>(List.of(tx)));

        var page = service.getByAccount(sourceId, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(txId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Transaction buildTx(UUID id, Transaction.Status status, Transaction.Type type) {
        return Transaction.builder()
                .id(id)
                .sourceAccountId(sourceId)
                .targetAccountId(type == Transaction.Type.TRANSFER ? targetId : null)
                .idempotencyKey(idempotencyKey)
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal("500.00"))
                .transactionFee(BigDecimal.ZERO)
                .build();
    }

    private TransactionRequest buildRequest(Transaction.Type type, UUID source, UUID target,
                                            UUID idempotency, BigDecimal amount) {
        TransactionRequest req = new TransactionRequest();
        ReflectionTestUtils.setField(req, "transactionType",  type);
        ReflectionTestUtils.setField(req, "sourceAccountId",  source);
        ReflectionTestUtils.setField(req, "targetAccountId",  target);
        ReflectionTestUtils.setField(req, "idempotencyKey",   idempotency);
        ReflectionTestUtils.setField(req, "amount",           amount);
        return req;
    }
}
