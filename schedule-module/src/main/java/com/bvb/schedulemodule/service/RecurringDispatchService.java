package com.bvb.schedulemodule.service;

import com.bvb.schedulemodule.domain.RecurringAuditLog;
import com.bvb.schedulemodule.domain.RecurringJob;
import com.bvb.schedulemodule.domain.RecurringSchedule;
import com.bvb.schedulemodule.repository.RecurringAuditLogRepository;
import com.bvb.schedulemodule.repository.RecurringJobRepository;
import com.bvb.schedulemodule.repository.RecurringScheduleRepository;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.service.DTO.request.TransactionRequest;
import com.bvb.transactionmodule.service.usecase.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Dispatches a single recurring schedule in its own transaction (REQUIRES_NEW).
 * Failures in one schedule do not roll back other schedules processed in the same cron tick.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringDispatchService {

    private final TransactionService          transactionService;
    private final RecurringScheduleRepository scheduleRepository;
    private final RecurringJobRepository      jobRepository;
    private final RecurringAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(RecurringSchedule schedule) {
        RecurringJob job     = schedule.getJob();
        long         startMs = System.currentTimeMillis();

        try {
            TransactionRequest req = buildRequest(job);
            transactionService.createTransaction(req, "RECURRING/" + job.getJobName());

            long    duration = System.currentTimeMillis() - startMs;
            Instant nextRun  = RecurringJobService.computeNextRun(
                    schedule.getCronExpression(), Instant.now());

            if (nextRun == null) {
                schedule.deactivate();
                log.info("[RECURRING] Schedule {} has no future runs — deactivated", schedule.getId());
            } else {
                schedule.recordSuccess(nextRun);
            }
            scheduleRepository.save(schedule);
            auditLogRepository.save(RecurringAuditLog.success(schedule, job.getAmount(), duration));
            log.info("[RECURRING] Dispatched jobId={} scheduleId={} nextRun={}",
                    job.getId(), schedule.getId(), nextRun);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startMs;
            schedule.recordFailure();

            if (schedule.getRetryCount() >= schedule.getMaxRetries()) {
                job.pause();
                jobRepository.save(job);
                schedule.deactivate();
                log.warn("[RECURRING] Job {} auto-paused after {} consecutive failures",
                        job.getId(), schedule.getRetryCount());
            }

            scheduleRepository.save(schedule);
            auditLogRepository.save(RecurringAuditLog.failed(schedule, e.getMessage(), duration));
            log.error("[RECURRING] Dispatch failed jobId={} attempt={} error={}",
                    job.getId(), schedule.getRetryCount(), e.getMessage());
        }
    }

    // ── Request builder ────────────────────────────────────────────────────────

    private TransactionRequest buildRequest(RecurringJob job) {
        return switch (job.getJobType()) {
            case TOP_UP       -> TransactionRequest.of(
                    job.getAccountId(), null, Transaction.Type.DEPOSIT, job.getAmount());
            case AUTO_DEBIT,
                 LOAN_PAYMENT -> TransactionRequest.of(
                    job.getAccountId(), null, Transaction.Type.WITHDRAWAL, job.getAmount());
            case SAVINGS      -> TransactionRequest.of(
                    job.getAccountId(), job.getTargetAccountId(), Transaction.Type.TRANSFER, job.getAmount());
        };
    }
}
