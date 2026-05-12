package com.bvb.schedulemodule.service.dto.response;

import com.bvb.schedulemodule.domain.RecurringJob;
import com.bvb.schedulemodule.domain.RecurringSchedule;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class RecurringJobResponse {

    private UUID                     id;
    private UUID                     accountId;
    private UUID                     targetAccountId;
    private String                   jobName;
    private RecurringJob.JobType     jobType;
    private BigDecimal               amount;
    private String                   description;
    private RecurringJob.Status      status;
    private Instant                  createdAt;
    private RecurringScheduleResponse schedule;

    public static RecurringJobResponse from(RecurringJob job, RecurringSchedule schedule) {
        return RecurringJobResponse.builder()
                .id(job.getId())
                .accountId(job.getAccountId())
                .targetAccountId(job.getTargetAccountId())
                .jobName(job.getJobName())
                .jobType(job.getJobType())
                .amount(job.getAmount())
                .description(job.getDescription())
                .status(job.getStatus())
                .createdAt(job.getCreatedAt())
                .schedule(schedule != null ? RecurringScheduleResponse.from(schedule) : null)
                .build();
    }

    public static RecurringJobResponse from(RecurringJob job) {
        return from(job, null);
    }
}
