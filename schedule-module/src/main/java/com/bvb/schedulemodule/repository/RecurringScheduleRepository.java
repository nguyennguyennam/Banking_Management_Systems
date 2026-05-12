package com.bvb.schedulemodule.repository;

import com.bvb.schedulemodule.domain.RecurringJob;
import com.bvb.schedulemodule.domain.RecurringSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringScheduleRepository extends JpaRepository<RecurringSchedule, UUID> {

    List<RecurringSchedule> findByJobId(UUID jobId);

    /**
     * Returns all active schedules whose next_run has passed, whose job is ACTIVE,
     * and that are within their valid window.
     */
    @Query("""
        SELECT s FROM RecurringSchedule s
        JOIN FETCH s.job j
        WHERE s.active    = true
          AND j.status    = :status
          AND s.nextRun  IS NOT NULL
          AND s.nextRun   <= :now
          AND s.validFrom <= :now
          AND (s.validUntil IS NULL OR s.validUntil > :now)
        """)
    List<RecurringSchedule> findDue(@Param("now") Instant now,
                                    @Param("status") RecurringJob.Status status);
}
