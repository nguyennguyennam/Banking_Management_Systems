package com.bvb.schedulemodule.service.dto.response;

import com.bvb.schedulemodule.domain.RecurringSchedule;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class RecurringScheduleResponse {

    private UUID    id;
    private UUID    jobId;
    private String  cronExpression;
    private boolean active;
    private int     maxRetries;
    private int     retryCount;
    private Instant nextRun;
    private Instant lastRun;
    private Instant validFrom;
    private Instant validUntil;

    public static RecurringScheduleResponse from(RecurringSchedule s) {
        return RecurringScheduleResponse.builder()
                .id(s.getId())
                .jobId(s.getJob().getId())
                .cronExpression(s.getCronExpression())
                .active(s.isActive())
                .maxRetries(s.getMaxRetries())
                .retryCount(s.getRetryCount())
                .nextRun(s.getNextRun())
                .lastRun(s.getLastRun())
                .validFrom(s.getValidFrom())
                .validUntil(s.getValidUntil())
                .build();
    }
}
