package com.bvb.accountmodule.controller;

import com.bvb.accountmodule.service.DTO.request.CreateAccountRequest;
import com.bvb.accountmodule.service.DTO.request.UpdateAccountRequest;
import com.bvb.accountmodule.service.DTO.response.AccountResponse;
import com.bvb.accountmodule.service.DTO.response.AccountStatsResponse;
import com.bvb.accountmodule.service.usecase.AccountUseCase;
import com.bvb.accountmodule.service.DTO.response.AccountAuditLogResponse;
import com.bvb.auditmodule.repository.AuditLogRepository;
import com.bvb.sharedmodule.security.SecurityUtils;
import com.bvb.sharedmodule.service.request.PageRequestHelper;
import com.bvb.sharedmodule.service.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Account management APIs")
public class AccountController {

    private final AccountUseCase accountUseCase;
    private final AuditLogRepository auditLogRepository;

    // ── CREATE ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create account for a customer")
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountUseCase.createAccount(request));
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<AccountResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        if ("USER".equals(SecurityUtils.resolveRole(user)))
            accountUseCase.verifyOwnership(id, user.getUsername());
        return ResponseEntity.ok(accountUseCase.getById(id));
    }

    @GetMapping("/{accountId}/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get audit log history for an account")
    public ResponseEntity<PageResponse<AccountAuditLogResponse>> getAuditLogs(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails user) {
        if ("USER".equals(SecurityUtils.resolveRole(user)))
            accountUseCase.verifyOwnership(accountId, user.getUsername());
        return ResponseEntity.ok(PageResponse.from(
                auditLogRepository.findByAccountId(
                                accountId,
                                PageRequestHelper.of(page, size, "changedAt", "desc"))
                        .map(AccountAuditLogResponse::from)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all accounts")
    public ResponseEntity<PageResponse<AccountResponse>> getAll(
            @RequestParam(defaultValue = "0")        int page,
            @RequestParam(defaultValue = "20")       int size,
            @RequestParam(defaultValue = "openedAt") String sortBy,
            @RequestParam(defaultValue = "desc")     String sortDir) {
        return ResponseEntity.ok(accountUseCase.getAll(
                PageRequestHelper.of(page, size, sortBy, sortDir)));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get accounts by customer ID")
    public ResponseEntity<PageResponse<AccountResponse>> getByCustomer(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(accountUseCase.getByCustomerId(
                customerId, PageRequestHelper.of(page, size)));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}/limit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update transaction limit")
    public ResponseEntity<AccountResponse> updateLimit(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(accountUseCase.updateLimit(id, request));
    }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lock account")
    public ResponseEntity<AccountResponse> lock(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountUseCase.lock(
                id, user.getUsername(), SecurityUtils.resolveRole(user)));
    }

    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Unlock account")
    public ResponseEntity<AccountResponse> unlock(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(accountUseCase.unlock(
                id, user.getUsername(), SecurityUtils.resolveRole(user)));
    }

    // ── SOFT DELETE ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Close account (soft delete)")
    public ResponseEntity<Void> close(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user) {
        accountUseCase.closeAccount(id, user.getUsername(), SecurityUtils.resolveRole(user));
        return ResponseEntity.noContent().build();
    }

    // ── STATS ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get account statistics by type and balance tier")
    public ResponseEntity<AccountStatsResponse> stats() {
        return ResponseEntity.ok(accountUseCase.getStats());
    }
}
