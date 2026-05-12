package com.bvb.schedulemodule.repository;

import com.bvb.schedulemodule.domain.RecurringAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecurringAuditLogRepository extends JpaRepository<RecurringAuditLog, UUID> {

    Page<RecurringAuditLog> findByScheduleId(UUID scheduleId, Pageable pageable);

    Page<RecurringAuditLog> findByScheduleJobId(UUID jobId, Pageable pageable);
}
