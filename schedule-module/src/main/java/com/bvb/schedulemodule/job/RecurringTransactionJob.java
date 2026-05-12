package com.bvb.schedulemodule.job;

import com.bvb.schedulemodule.domain.RecurringJob;
import com.bvb.schedulemodule.domain.RecurringSchedule;
import com.bvb.schedulemodule.repository.RecurringScheduleRepository;
import com.bvb.schedulemodule.service.RecurringDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Every minute, finds all due recurring schedules and dispatches their transactions.
 * Each schedule is processed in its own REQUIRES_NEW transaction via RecurringDispatchService,
 * so a single failure does not abort the rest of the batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringTransactionJob {

    private final RecurringScheduleRepository scheduleRepository;
    private final RecurringDispatchService    dispatchService;

    @Scheduled(fixedDelay = 60_000)
    public void execute() {
        Instant now = Instant.now();
        List<RecurringSchedule> due = scheduleRepository.findDue(now, RecurringJob.Status.ACTIVE);

        if (due.isEmpty()) return;

        log.info("[RECURRING] Tick: {} schedule(s) due at {}", due.size(), now);

        for (RecurringSchedule schedule : due) {
            try {
                dispatchService.dispatch(schedule);
            } catch (Exception e) {
                // dispatch() should never propagate — log as a safety net
                log.error("[RECURRING] Unexpected error dispatching scheduleId={}: {}",
                        schedule.getId(), e.getMessage());
            }
        }
    }
}
