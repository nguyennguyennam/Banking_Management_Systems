package com.bvb.schedulemodule.service;

import com.bvb.schedulemodule.domain.RecurringJob;
import com.bvb.schedulemodule.domain.RecurringSchedule;
import com.bvb.schedulemodule.repository.RecurringJobRepository;
import com.bvb.schedulemodule.repository.RecurringScheduleRepository;
import com.bvb.schedulemodule.service.dto.request.RecurringJobRequest;
import com.bvb.schedulemodule.service.dto.response.RecurringJobResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringJobService {

    private final RecurringJobRepository      jobRepository;
    private final RecurringScheduleRepository scheduleRepository;

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public RecurringJobResponse create(RecurringJobRequest req) {
        validateRequest(req);

        RecurringJob job = RecurringJob.builder()
                .id(UUID.randomUUID())
                .accountId(req.getAccountId())
                .targetAccountId(req.getTargetAccountId())
                .jobName(req.getJobName())
                .jobType(req.getJobType())
                .amount(req.getAmount())
                .description(req.getDescription())
                .status(RecurringJob.Status.ACTIVE)
                .build();
        jobRepository.save(job);

        Instant validFrom = req.getValidFrom() != null ? req.getValidFrom() : Instant.now();
        Instant firstRun  = computeNextRun(req.getCronExpression(), validFrom);
        int     maxRetries = req.getMaxRetries() > 0 ? req.getMaxRetries() : 3;

        RecurringSchedule schedule = RecurringSchedule.builder()
                .id(UUID.randomUUID())
                .job(job)
                .cronExpression(req.getCronExpression())
                .active(true)
                .maxRetries(maxRetries)
                .retryCount(0)
                .nextRun(firstRun)
                .validFrom(validFrom)
                .validUntil(req.getValidUntil())
                .build();
        scheduleRepository.save(schedule);

        log.info("[RECURRING] Created jobId={} type={} cron={} firstRun={}",
                job.getId(), job.getJobType(), req.getCronExpression(), firstRun);
        return RecurringJobResponse.from(job, schedule);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RecurringJobResponse getById(UUID id) {
        RecurringJob job = findJobOrThrow(id);
        List<RecurringSchedule> schedules = scheduleRepository.findByJobId(id);
        return RecurringJobResponse.from(job, schedules.isEmpty() ? null : schedules.get(0));
    }

    @Transactional(readOnly = true)
    public Page<RecurringJobResponse> getByAccount(UUID accountId, Pageable pageable) {
        return jobRepository.findByAccountId(accountId, pageable)
                .map(job -> {
                    List<RecurringSchedule> schedules = scheduleRepository.findByJobId(job.getId());
                    return RecurringJobResponse.from(job, schedules.isEmpty() ? null : schedules.get(0));
                });
    }

    // ── State transitions ──────────────────────────────────────────────────────

    @Transactional
    public RecurringJobResponse pause(UUID id) {
        RecurringJob job = findJobOrThrow(id);
        job.pause();
        jobRepository.save(job);
        log.info("[RECURRING] Paused jobId={}", id);
        return RecurringJobResponse.from(job);
    }

    @Transactional
    public RecurringJobResponse activate(UUID id) {
        RecurringJob job = findJobOrThrow(id);
        if (job.getStatus() == RecurringJob.Status.CANCELLED)
            throw new IllegalStateException("Cannot reactivate a CANCELLED job");
        job.activate();
        jobRepository.save(job);
        log.info("[RECURRING] Activated jobId={}", id);
        return RecurringJobResponse.from(job);
    }

    @Transactional
    public RecurringJobResponse cancel(UUID id) {
        RecurringJob job = findJobOrThrow(id);
        job.cancel();
        scheduleRepository.findByJobId(id).forEach(s -> {
            s.deactivate();
            scheduleRepository.save(s);
        });
        jobRepository.save(job);
        log.info("[RECURRING] Cancelled jobId={}", id);
        return RecurringJobResponse.from(job);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private RecurringJob findJobOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RecurringJob not found: " + id));
    }

    private void validateRequest(RecurringJobRequest req) {
        // throws IllegalArgumentException if cron expression is invalid
        CronExpression.parse(req.getCronExpression());
        if (req.getJobType() == RecurringJob.JobType.SAVINGS && req.getTargetAccountId() == null)
            throw new IllegalArgumentException("SAVINGS job type requires targetAccountId");
        if (req.getValidUntil() != null && req.getValidFrom() != null
                && req.getValidUntil().isBefore(req.getValidFrom()))
            throw new IllegalArgumentException("validUntil must be after validFrom");
    }

    // ── Static utility (also used by RecurringDispatchService) ────────────────

    public static Instant computeNextRun(String cronExpr, Instant from) {
        CronExpression cron   = CronExpression.parse(cronExpr);
        ZonedDateTime  next   = cron.next(from.atZone(ZoneOffset.UTC));
        return next != null ? next.toInstant() : null;
    }
}
