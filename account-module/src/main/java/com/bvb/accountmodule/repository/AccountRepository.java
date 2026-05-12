package com.bvb.accountmodule.repository;

import com.bvb.accountmodule.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByIdAndCustomerId(UUID id, UUID customerId);

    Page<Account> findByCustomerIdAndStatusNot(UUID customerId,
                                               Account.Status status,
                                               Pageable pageable);

    // ── Pessimistic lock for balance mutations ────────────────────────────────

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);

    // ── Updates ───────────────────────────────────────────────────────────────

    @Query(value = "SELECT MAX(CAST(account_number AS BIGINT)) FROM account", nativeQuery = true)
    Optional<Long> findMaxAccountNumberSuffix();

    @Modifying
    @Query("UPDATE Account a SET a.status = :status, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    void updateAccountStatus(@Param("id") UUID id, @Param("status") Account.Status status);

    @Modifying
    @Query("UPDATE Account a SET a.transactionLimit = :transactionLimit, a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    void updateAccountTransactionLimit(@Param("id") UUID id,
                                       @Param("transactionLimit") BigDecimal transactionLimit);

    @Modifying
    @Query("UPDATE Account a SET a.status = 'CLOSED', a.updatedAt = CURRENT_TIMESTAMP WHERE a.id = :id")
    void softDelete(@Param("id") UUID id);

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Query("SELECT a.accountType, COUNT(a) FROM Account a GROUP BY a.accountType")
    List<Object[]> countByType();

    @Query("""
        SELECT
            CASE
                WHEN a.balance < 500000 THEN 'LOW'
                WHEN a.balance >= 500000 AND a.balance < 10000000 THEN 'MEDIUM'
                ELSE 'HIGH'
            END AS balanceLevel,
            COUNT(a) AS count
        FROM Account a
        GROUP BY
            CASE
                WHEN a.balance < 500000 THEN 'LOW'
                WHEN a.balance >= 500000 AND a.balance < 10000000 THEN 'MEDIUM'
                ELSE 'HIGH'
            END
        """)
    List<Object[]> getStats();
}
