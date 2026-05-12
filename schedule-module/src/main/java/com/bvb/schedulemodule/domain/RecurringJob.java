package com.bvb.schedulemodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recurring_job")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecurringJob {

    public enum JobType { AUTO_DEBIT, SAVINGS, LOAN_PAYMENT, TOP_UP }
    public enum Status  { ACTIVE, PAUSED, CANCELLED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "target_account_id")
    private UUID targetAccountId;

    @Column(name = "job_name", nullable = false, length = 255)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "job_type::text", write = "?::recurring_job_type_")
    @Column(name = "job_type", nullable = false, columnDefinition = "RECURRING_JOB_TYPE_")
    private JobType jobType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "status::text", write = "?::recurring_job_status_")
    @Column(name = "status", nullable = false, columnDefinition = "RECURRING_JOB_STATUS_")
    private Status status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Business methods ───────────────────────────────────────────────────────

    public void pause()    { this.status = Status.PAUSED; }
    public void activate() { this.status = Status.ACTIVE; }
    public void cancel()   { this.status = Status.CANCELLED; }
}
