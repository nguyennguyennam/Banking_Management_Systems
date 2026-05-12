package com.bvb.transactionmodule.controller;

import com.bvb.accountmodule.service.usecase.AccountUseCase;
import com.bvb.sharedmodule.security.SecurityUtils;
import com.bvb.sharedmodule.service.request.PageRequestHelper;
import com.bvb.sharedmodule.service.response.PageResponse;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.service.DTO.request.RollbackRequest;
import com.bvb.transactionmodule.service.DTO.request.TransactionRequest;
import com.bvb.transactionmodule.service.DTO.request.TransactionSearchRequest;
import com.bvb.transactionmodule.service.DTO.response.RollbackResponse;
import com.bvb.transactionmodule.service.DTO.response.TransactionResponse;
import com.bvb.transactionmodule.service.usecase.TransactionRollbackService;
import com.bvb.transactionmodule.service.usecase.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "Transaction management APIs")
public class TransactionController {

    private final TransactionService         transactionService;
    private final TransactionRollbackService rollbackService;
    private final AccountUseCase             accountUseCase;

    // ── CREATE ─────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Create a new transaction (DEPOSIT / WITHDRAWAL / TRANSFER)")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal UserDetails user) {
        if ("USER".equals(SecurityUtils.resolveRole(user)))
            accountUseCase.verifyOwnership(request.getSourceAccountId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(request, user.getUsername()));
    }

    // ── READ ───────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get paginated transaction history for an account")
    public ResponseEntity<PageResponse<TransactionResponse>> getByAccount(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails user) {
        if ("USER".equals(SecurityUtils.resolveRole(user)))
            accountUseCase.verifyOwnership(accountId, user.getUsername());
        Page<TransactionResponse> result = transactionService.getByAccount(
                accountId, PageRequestHelper.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }

    // ── SEARCH ────────────────────────────────────────────────────────────────

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Search transactions — filter by amount, type, status, date ranges; supports pagination and sorting")
    public ResponseEntity<PageResponse<TransactionResponse>> search(
            @RequestParam(required = false) UUID sourceAccountId,
            @RequestParam(required = false) Transaction.Type type,
            @RequestParam(required = false) Transaction.Status status,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant executedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant executedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0")          int page,
            @RequestParam(defaultValue = "20")         int size,
            @RequestParam(defaultValue = "createdAt")  String sortBy,
            @RequestParam(defaultValue = "desc")       String sortDir,
            @AuthenticationPrincipal UserDetails user) {
        if ("USER".equals(SecurityUtils.resolveRole(user)))
            accountUseCase.verifyOwnership(sourceAccountId, user.getUsername());
        TransactionSearchRequest req = TransactionSearchRequest.builder()
                .sourceAccountId(sourceAccountId)
                .type(type)
                .status(status)
                .amountMin(amountMin)
                .amountMax(amountMax)
                .executedFrom(executedFrom)
                .executedTo(executedTo)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .build();

        Page<TransactionResponse> result = transactionService.search(req, page, size, sortBy, sortDir);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    // ── ROLLBACK ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rollback a COMPLETED transaction (ADMIN only)")
    public ResponseEntity<RollbackResponse> rollback(
            @PathVariable UUID id,
            @Valid @RequestBody RollbackRequest request,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                rollbackService.rollback(id, request.getReason(), user.getUsername()));
    }
}
