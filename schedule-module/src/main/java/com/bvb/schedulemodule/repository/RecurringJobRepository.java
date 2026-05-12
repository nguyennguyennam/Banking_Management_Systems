package com.bvb.schedulemodule.repository;

import com.bvb.schedulemodule.domain.RecurringJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecurringJobRepository extends JpaRepository<RecurringJob, UUID> {

    Page<RecurringJob> findByAccountId(UUID accountId, Pageable pageable);

    Page<RecurringJob> findByStatus(RecurringJob.Status status, Pageable pageable);
}
