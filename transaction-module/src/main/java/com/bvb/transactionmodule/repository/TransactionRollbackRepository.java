package com.bvb.transactionmodule.repository;

import com.bvb.transactionmodule.domain.TransactionRollback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRollbackRepository extends JpaRepository<TransactionRollback, UUID> {

    List<TransactionRollback> findByOriginalTransactionIdOrderByCreatedAtAsc(UUID originalTransactionId);

    int countByOriginalTransactionId(UUID originalTransactionId);

    boolean existsByOriginalTransactionIdAndStatus(UUID originalTransactionId,
                                                   TransactionRollback.Status status);

    default boolean hasCompleted(UUID originalTransactionId) {
        return existsByOriginalTransactionIdAndStatus(
                originalTransactionId, TransactionRollback.Status.COMPLETED);
    }
}
