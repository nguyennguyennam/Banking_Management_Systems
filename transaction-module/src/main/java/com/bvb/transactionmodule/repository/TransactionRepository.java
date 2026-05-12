package com.bvb.transactionmodule.repository;

import com.bvb.transactionmodule.domain.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    // ── Idempotency ───────────────────────────────────────────────────────────

    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    // ── Pessimistic lock ──────────────────────────────────────────────────────

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdWithLock(@Param("id") UUID id);

    // ── Status transitions ────────────────────────────────────────────────────
    // Native SQL with explicit ::transaction_status_ casts avoids Hibernate's
    // uncertain @ColumnTransformer application in JPQL bulk-UPDATE WHERE clauses.

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE transaction
        SET transaction_status = 'COMPLETED'::transaction_status_,
            transaction_fee    = :fee,
            executed_at        = CURRENT_TIMESTAMP,
            updated_at         = CURRENT_TIMESTAMP
        WHERE id = :id
          AND transaction_status = 'PENDING'::transaction_status_
        """, nativeQuery = true)
    int markCompleted(@Param("id") UUID id, @Param("fee") BigDecimal fee);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE transaction
        SET transaction_status = 'FAILED'::transaction_status_,
            updated_at         = CURRENT_TIMESTAMP
        WHERE id = :id
          AND transaction_status = 'PENDING'::transaction_status_
        """, nativeQuery = true)
    int markFailed(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE transaction
        SET transaction_status = 'ROLLED_BACK'::transaction_status_,
            updated_at         = CURRENT_TIMESTAMP
        WHERE id = :id
          AND transaction_status = 'COMPLETED'::transaction_status_
        """, nativeQuery = true)
    int markRolledBack(@Param("id") UUID id);

    // ── Reconciliation ────────────────────────────────────────────────────────

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.transactionStatus = 'PENDING'
          AND t.createdAt < :cutoff
        ORDER BY t.createdAt ASC
        """)
    List<Transaction> findStalePending(@Param("cutoff") Instant cutoff, Pageable pageable);

    // ── Query by account ──────────────────────────────────────────────────────

    Page<Transaction> findBySourceAccountId(UUID sourceAccountId, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.sourceAccountId = :accountId
           OR t.targetAccountId = :accountId
        ORDER BY t.createdAt DESC
        """)
    Page<Transaction> findAllByAccountId(@Param("accountId") UUID accountId, Pageable pageable);

    // ── Query by status ───────────────────────────────────────────────────────

    Page<Transaction> findByTransactionStatus(Transaction.Status status, Pageable pageable);
}
