package com.bvb.schedulemodule.controller;

import com.bvb.schedulemodule.domain.RecurringAuditLog;
import com.bvb.schedulemodule.repository.RecurringAuditLogRepository;
import com.bvb.schedulemodule.service.RecurringJobService;
import com.bvb.schedulemodule.service.dto.request.RecurringJobRequest;
import com.bvb.schedulemodule.service.dto.response.RecurringJobResponse;
import com.bvb.sharedmodule.service.request.PageRequestHelper;
import com.bvb.sharedmodule.service.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-jobs")
@RequiredArgsConstructor
@Tag(name = "Recurring Jobs", description = "Scheduled / recurring transaction management")
public class RecurringJobController {

    private final RecurringJobService       jobService;
    private final RecurringAuditLogRepository auditLogRepository;

    // ── CREATE ─────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Create a recurring job (e.g. monthly top-up, auto-debit, loan payment)")
    public ResponseEntity<RecurringJobResponse> create(
            @Valid @RequestBody RecurringJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.create(request));
    }

    // ── READ ───────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get recurring job by ID (includes schedule details)")
    public ResponseEntity<RecurringJobResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getById(id));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "List all recurring jobs for an account")
    public ResponseEntity<PageResponse<RecurringJobResponse>> getByAccount(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<RecurringJobResponse> result =
                jobService.getByAccount(accountId, PageRequestHelper.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }

    // ── STATE TRANSITIONS ──────────────────────────────────────────────────────

    @PatchMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Pause a recurring job (stops future executions until re-activated)")
    public ResponseEntity<RecurringJobResponse> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.pause(id));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Re-activate a paused recurring job")
    public ResponseEntity<RecurringJobResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.activate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Cancel a recurring job permanently")
    public ResponseEntity<RecurringJobResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.cancel(id));
    }

    // ── HISTORY ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get execution history for a recurring job")
    public ResponseEntity<PageResponse<RecurringAuditLog>> getHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<RecurringAuditLog> result =
                auditLogRepository.findByScheduleJobId(id, PageRequestHelper.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }
}
