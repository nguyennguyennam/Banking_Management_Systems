package com.bvb.schedulemodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recurring_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RecurringAuditLog {

    public enum RunStatus { SUCCESS, FAILED, SKIPPED }

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private RecurringSchedule schedule;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "status::text", write = "?::recurring_run_status_")
    @Column(name = "status", nullable = false, columnDefinition = "RECURRING_RUN_STATUS_")
    private RunStatus status;

    @Column(name = "result_message", columnDefinition = "TEXT")
    private String resultMessage;

    @Column(name = "amount_processed", precision = 18, scale = 2)
    private BigDecimal amountProcessed;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    // ── Factory methods ────────────────────────────────────────────────────────

    public static RecurringAuditLog success(RecurringSchedule schedule, BigDecimal amount, long durationMs) {
        return RecurringAuditLog.builder()
                .id(UUID.randomUUID())
                .schedule(schedule)
                .status(RunStatus.SUCCESS)
                .amountProcessed(amount)
                .durationMs((int) durationMs)
                .executedAt(Instant.now())
                .build();
    }

    public static RecurringAuditLog failed(RecurringSchedule schedule, String message, long durationMs) {
        return RecurringAuditLog.builder()
                .id(UUID.randomUUID())
                .schedule(schedule)
                .status(RunStatus.FAILED)
                .resultMessage(message)
                .durationMs((int) durationMs)
                .executedAt(Instant.now())
                .build();
    }
}
