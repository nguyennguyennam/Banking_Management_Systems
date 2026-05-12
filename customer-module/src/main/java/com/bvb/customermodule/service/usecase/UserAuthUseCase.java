package com.bvb.customermodule.service.usecase;

import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.customermodule.service.DTO.request.UpdateUserAuthRequest;
import com.bvb.customermodule.service.DTO.request.UpdateUserStatusRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthUseCase {

    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;

    // -- Login


    // ── CHANGE PASSWORD ───────────────────────────────────────────────────────

    @Transactional
    public void changePassword(UUID customerId,
                               UpdateUserAuthRequest request,
                               UserAuth currentUser) {
        if (!currentUser.getCustomerId().equals(customerId))
            throw new AccessDeniedException("Cannot modify other customer's credentials");

        if (!request.getNewPassword().equals(request.getConfirmPassword()))
            throw new IllegalArgumentException("Passwords do not match");

        UserAuth userAuth = userAuthRepository.findByUsername(currentUser.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), userAuth.getPassword()))
            throw new IllegalArgumentException("Current password is incorrect");

        userAuthRepository.updatePassword(userAuth.getId(),
                passwordEncoder.encode(request.getNewPassword()));

        log.info("Password changed for customer: {}", customerId);
    }

    // ── UPDATE STATUS — ADMIN only ────────────────────────────────────────────
    // username = phone number (unique)

    @Transactional
    public void updateStatus(String username, UpdateUserStatusRequest request) {
        userAuthRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        userAuthRepository.updateActive(username, request.getActive());
        log.info("User {} status updated to {}", username, request.getActive());
    }

    // ── DELETE — ADMIN only ───────────────────────────────────────────────────
    // Soft delete — deactivate by username

    @Transactional
    public void deleteCustomer(String username) {
        userAuthRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        userAuthRepository.updateActive(username, false);
        log.info("Customer deactivated: {}", username);
    }
}