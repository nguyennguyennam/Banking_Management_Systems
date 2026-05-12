package com.bvb.transactionmodule.service.usecase;

import com.bvb.transactionmodule.domain.TransactionRollback;
import com.bvb.transactionmodule.repository.TransactionRollbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists rollback audit records in an independent transaction so that a
 * FAILED record is committed even when the caller's transaction rolls back.
 */
@Service
@RequiredArgsConstructor
public class RollbackPersistenceService {

    private final TransactionRollbackRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailed(UUID originalTransactionId, String reason, String initiatedBy) {
        repository.save(TransactionRollback.failed(originalTransactionId, reason, initiatedBy));
    }
}
