package com.bvb.customermodule.controller;

import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.service.DTO.request.CreateCustomerRequest;
import com.bvb.customermodule.service.DTO.request.UpdateCustomerRequest;
import com.bvb.customermodule.service.DTO.response.CustomerResponse;
import com.bvb.customermodule.service.usecase.CustomerUseCase;
import com.bvb.sharedmodule.service.response.PageResponse;
import com.bvb.sharedmodule.service.request.PageRequestHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer management APIs")
public class CustomerController {

    private final CustomerUseCase customerUseCase;

    // ── CREATE ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create customer")
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerUseCase.createCustomer(request));
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<CustomerResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerUseCase.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all customers")
    public ResponseEntity<PageResponse<CustomerResponse>> getAll(
            @RequestParam(defaultValue = "0")         int page,
            @RequestParam(defaultValue = "10")        int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")      String sortDir) {
        Pageable pageable = PageRequestHelper.of(page, size, sortBy, sortDir);
        return ResponseEntity.ok(customerUseCase.getAll(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search customers by name")
    public ResponseEntity<PageResponse<CustomerResponse>> searchByName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(customerUseCase.searchByName(
                name, PageRequestHelper.of(page, size)));
    }

    @GetMapping("/city")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get customers by city")
    public ResponseEntity<PageResponse<CustomerResponse>> getByCity(
            @RequestParam String name,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(customerUseCase.getByCity(
                name, PageRequestHelper.of(page, size)));
    }

    // ── STATS ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats/city")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Count customers by city")
    public ResponseEntity<Map<String, Long>> countByCity() {
        return ResponseEntity.ok(customerUseCase.countByCity());
    }

    @GetMapping("/stats/type")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Count customers by type")
    public ResponseEntity<Map<String, Long>> countByType() {
        return ResponseEntity.ok(customerUseCase.countByType());
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Update customer profile")
    public ResponseEntity<CustomerResponse> updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request,
            @AuthenticationPrincipal UserAuth currentUser) {
        return ResponseEntity.ok(customerUseCase.updateProfile(id, request, currentUser));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete customer")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserAuth currentUser) {
        customerUseCase.deleteCustomer(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}