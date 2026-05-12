package com.bvb.auditmodule.repository;

import com.bvb.auditmodule.domain.AuditLog;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityId(UUID entityId, Pageable pageable);

    Page<AuditLog> findByEntityType(AuditLogEvent.EntityType entityType, Pageable pageable);

    Page<AuditLog> findByChangedBy(String changedBy, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityId(AuditLogEvent.EntityType entityType,
                                               UUID entityId,
                                               Pageable pageable);

    Page<AuditLog> findByAccountId(UUID accountId, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndAccountId(AuditLogEvent.EntityType entityType,
                                                UUID accountId,
                                                Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM accounts WHERE id = :accountId AND customer_id = :customerId",
           nativeQuery = true)
    long countByAccountAndCustomer(@Param("accountId") UUID accountId,
                                   @Param("customerId") UUID customerId);
}