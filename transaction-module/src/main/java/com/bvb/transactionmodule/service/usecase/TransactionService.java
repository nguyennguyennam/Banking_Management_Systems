package com.bvb.transactionmodule.service.usecase;

import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.service.DTO.request.TransactionRequest;
import com.bvb.transactionmodule.service.DTO.request.TransactionSearchRequest;
import com.bvb.transactionmodule.service.DTO.response.TransactionResponse;
import com.bvb.transactionmodule.config.exception.TransactionNotFoundException;
import com.bvb.transactionmodule.repository.TransactionRepository;
import com.bvb.transactionmodule.repository.TransactionSpec;
import com.bvb.sharedmodule.service.request.PageRequestHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

// PostgreSQL SQL state codes
// 23505 = unique_violation (idempotency key race)
// 23503 = foreign_key_violation (account not found)

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "amount", "transactionType", "transactionStatus", "executedAt", "createdAt");

    private final TransactionRepository   transactionRepository;
    private final TransactionStateService stateService;

    /**
     * All operations are event-driven (PENDING-first, async):
     *   1. Idempotency key check — returns existing tx if already present.
     *   2. INSERT PENDING (REQUIRES_NEW → commit).
     *   3. Publish operation-specific event (REQUIRES_NEW → commit).
     *   4. Return PENDING to client immediately.
     *
     * account-module handles balance mutations and publishes the result event.
     * TransferResultHandler marks the tx COMPLETED or FAILED asynchronously.
     * Schedule-module reconciles any PENDING tx older than 2 minutes.
     */
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest req, String performedBy) {
        validateRequest(req);

        return transactionRepository.findByIdempotencyKey(req.getIdempotencyKey())
                .map(existing -> {
                    log.info("[TRANSACTION] Idempotent hit key={} → tx={}", req.getIdempotencyKey(), existing.getId());
                    return TransactionResponse.from(existing);
                })
                .orElseGet(() -> insertNew(req, performedBy));
    }

    private TransactionResponse insertNew(TransactionRequest req, String performedBy) {
        UUID txId = UUID.randomUUID();

        Transaction pendingTx;
        try {
            pendingTx = stateService.savePending(txId, req);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                // Concurrent request with same idempotency key — look up the winning insert
                log.warn("[TRANSACTION] Idempotency key race key={}", req.getIdempotencyKey());
                return transactionRepository.findByIdempotencyKey(req.getIdempotencyKey())
                        .map(TransactionResponse::from)
                        .orElseThrow(() -> new IllegalStateException("Idempotency race unresolvable"));
            }
            // FK violation (account not found) or check constraint — surface it clearly
            throw new IllegalArgumentException(extractConstraintMessage(e));
        }

        switch (req.getTransactionType()) {
            case DEPOSIT -> {
                stateService.publishDepositRequested(txId, req.getSourceAccountId(), req.getAmount(), performedBy);
                log.info("[TRANSACTION] DEPOSIT queued async txId={}", txId);
            }
            case WITHDRAWAL -> {
                stateService.publishWithdrawalRequested(txId, req.getSourceAccountId(), req.getAmount(), performedBy);
                log.info("[TRANSACTION] WITHDRAWAL queued async txId={}", txId);
            }
            case TRANSFER -> {
                //Check for transfer money
                if (req.getSourceAccountId() != req.getTargetAccountId()) {
                    throw new IllegalArgumentException("Cannot transfer to same account");
                }

                stateService.publishTransferRequested(
                        txId, req.getSourceAccountId(), req.getTargetAccountId(), req.getAmount(), performedBy);
                log.info("[TRANSACTION] TRANSFER queued async txId={}", txId);
            }
        }
        return TransactionResponse.from(pendingTx);
    }

    // ── Query ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "transaction", key = "#id")
    public TransactionResponse getById(UUID id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "transaction-history",
               key = "#accountId.toString() + ':' + #pageable.pageNumber")
    public Page<TransactionResponse> getByAccount(UUID accountId, Pageable pageable) {
        return transactionRepository.findAllByAccountId(accountId, pageable)
                .map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(TransactionSearchRequest req,
                                            int page, int size,
                                            String sortBy, String sortDir) {
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) sortBy = "createdAt";
        Pageable pageable = PageRequestHelper.of(page, size, sortBy, sortDir);
        return transactionRepository.findAll(TransactionSpec.from(req), pageable)
                .map(TransactionResponse::from);
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private void validateRequest(TransactionRequest req) {
        if (req.getTransactionType() == Transaction.Type.TRANSFER
                && req.getTargetAccountId() == null) {
            throw new IllegalArgumentException("TRANSFER transaction requires targetAccountId");
        }
        if (req.getTransactionType() == Transaction.Type.TRANSFER
                && req.getSourceAccountId().equals(req.getTargetAccountId())) {
            throw new IllegalArgumentException("Source and target accounts must be different");
        }
    }

    // ── Constraint-violation helpers ────────────────────────────────────────────

    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        // PostgreSQL SQL state 23505 = unique_violation
        String msg = e.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("duplicate key");
    }

    private static String extractConstraintMessage(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        if (msg != null) {
            if (msg.contains("source_account_id")) return "Source account not found";
            if (msg.contains("target_account_id")) return "Target account not found";
        }
        return "Invalid transaction data";
    }
}
