package com.bvb.schedulemodule.service.dto.request;

import com.bvb.schedulemodule.domain.RecurringJob;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class RecurringJobRequest {

    @NotNull
    private UUID accountId;

    /** Required when jobType = SAVINGS (transfer to savings account). */
    private UUID targetAccountId;

    @NotBlank
    private String jobName;

    @NotNull
    private RecurringJob.JobType jobType;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount phải > 0")
    private BigDecimal amount;

    private String description;

    /**
     * Spring 6-field cron expression (second minute hour day-of-month month day-of-week).
     * Examples:
     *   "0 0 0 1 * *"  — monthly on the 1st at midnight UTC
     *   "0 0 9 * * MON" — every Monday at 09:00 UTC
     *   "0 0 8 * * *"  — daily at 08:00 UTC
     */
    @NotBlank
    private String cronExpression;

    /** Start of validity window; defaults to now if null. */
    private Instant validFrom;

    /** End of validity window; null = no expiry. */
    private Instant validUntil;

    /** Max dispatch failures before the job is auto-paused. Default 3. */
    private int maxRetries = 3;
}
