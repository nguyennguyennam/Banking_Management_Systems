package com.bvb.customermodule.controller;

import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.service.DTO.request.UpdateUserAuthRequest;
import com.bvb.customermodule.service.DTO.request.UpdateUserStatusRequest;
import com.bvb.customermodule.service.usecase.UserAuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "UserAuth", description = "User authentication management APIs")
public class UserAuthController {

    private final UserAuthUseCase userAuthUseCase;

    @PatchMapping("/{customerId}/password")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Change password")
    public ResponseEntity<Void> changePassword(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateUserAuthRequest request,
            @AuthenticationPrincipal UserAuth currentUser) {
        userAuthUseCase.changePassword(customerId, request, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{username}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user active status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        userAuthUseCase.updateStatus(username, request);
        return ResponseEntity.noContent().build();
    }
}