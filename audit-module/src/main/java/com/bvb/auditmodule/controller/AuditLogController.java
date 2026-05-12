package com.bvb.auditmodule.controller;

import com.bvb.auditmodule.repository.AuditLogRepository;
import com.bvb.auditmodule.service.DTO.request.AuditSearchRequest;
import com.bvb.auditmodule.service.DTO.response.AuditLogResponse;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import com.bvb.sharedmodule.security.UserPrincipal;
import com.bvb.sharedmodule.service.request.PageRequestHelper;
import com.bvb.sharedmodule.service.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit log APIs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    // ── GET ALL (ADMIN only) ──────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all audit logs with pagination")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAll(
            @RequestBody AuditSearchRequest request) {

        Pageable pageable = PageRequestHelper.of(
                request.getPage(),
                request.getSize(),
                request.getSortBy(),
                request.getSortDir());

        return ResponseEntity.ok(PageResponse.from(
                auditLogRepository.findAll(pageable)
                        .map(AuditLogResponse::from)));
    }

    // ── GET BY ENTITY TYPE + ACCOUNT ID ──────────────────────────────────────

    @GetMapping("/entity")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get audit history by entity type and account ID")
    public ResponseEntity<PageResponse<AuditLogResponse>> getByEntity(
            @RequestParam AuditLogEvent.EntityType entityType,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        Pageable pageable = PageRequestHelper.of(page, size, "changedAt", "desc");

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            if (accountId == null)
                throw new IllegalArgumentException("accountId is required");

            if (auditLogRepository.countByAccountAndCustomer(accountId, currentUser.getCustomerId()) == 0)
                throw new AccessDeniedException("Access denied to account: " + accountId);

            return ResponseEntity.ok(PageResponse.from(
                    auditLogRepository.findByEntityTypeAndAccountId(entityType, accountId, pageable)
                            .map(AuditLogResponse::fromForUser)));
        }

        // ADMIN path
        if (accountId != null) {
            return ResponseEntity.ok(PageResponse.from(
                    auditLogRepository.findByEntityTypeAndAccountId(entityType, accountId, pageable)
                            .map(AuditLogResponse::from)));
        }

        return ResponseEntity.ok(PageResponse.from(
                auditLogRepository.findByEntityType(entityType, pageable)
                        .map(AuditLogResponse::from)));
    }
}
