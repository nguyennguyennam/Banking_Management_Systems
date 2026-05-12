package com.bvb.schedulemodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recurring_schedule")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecurringSchedule {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, updatable = false)
    private RecurringJob job;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_run")
    private Instant nextRun;

    @Column(name = "last_run")
    private Instant lastRun;

    @CreatedDate
    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    // ── Business methods ───────────────────────────────────────────────────────

    public void recordSuccess(Instant computedNextRun) {
        this.lastRun    = Instant.now();
        this.nextRun    = computedNextRun;
        this.retryCount = 0;
    }

    public void recordFailure() {
        this.retryCount++;
    }

    public void deactivate() {
        this.active = false;
    }
}
