package com.bvb.customermodule.service;

import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.customermodule.service.DTO.request.UpdateUserAuthRequest;
import com.bvb.customermodule.service.DTO.request.UpdateUserStatusRequest;
import com.bvb.customermodule.service.usecase.UserAuthUseCase;
import com.bvb.sharedmodule.security.Role;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuthUseCase")
class UserAuthUseCaseTest {

    @Mock UserAuthRepository userAuthRepository;
    @Mock PasswordEncoder    passwordEncoder;

    @InjectMocks UserAuthUseCase useCase;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserAuth userAuth(UUID ownedCustomerId) {
        return UserAuth.builder()
                .id(UUID.randomUUID())
                .customerId(ownedCustomerId)
                .username("0901234567")
                .passwordHash("$2a$10$hashedCurrent")
                .role(Role.USER)
                .active(true)
                .build();
    }

    private UpdateUserAuthRequest changePasswordRequest(String current, String newPwd, String confirm) {
        UpdateUserAuthRequest req = new UpdateUserAuthRequest();
        ReflectionTestUtils.setField(req, "currentPassword", current);
        ReflectionTestUtils.setField(req, "newPassword",     newPwd);
        ReflectionTestUtils.setField(req, "confirmPassword", confirm);
        return req;
    }

    private UpdateUserStatusRequest statusRequest(boolean active) {
        UpdateUserStatusRequest req = new UpdateUserStatusRequest();
        ReflectionTestUtils.setField(req, "active", active);
        return req;
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("different customerId throws AccessDeniedException")
        void differentCustomerId_throws() {
            UserAuth currentUser = userAuth(UUID.randomUUID());
            UUID     targetId    = customerId;

            assertThatThrownBy(() -> useCase.changePassword(
                    targetId,
                    changePasswordRequest("current", "newPass1", "newPass1"),
                    currentUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Cannot modify other customer");
        }

        @Test
        @DisplayName("new password and confirm do not match throws IllegalArgumentException")
        void passwordMismatch_throws() {
            UserAuth currentUser = userAuth(customerId);

            assertThatThrownBy(() -> useCase.changePassword(
                    customerId,
                    changePasswordRequest("current", "newPass1", "newPass2"),
                    currentUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Passwords do not match");
        }

        @Test
        @DisplayName("wrong current password throws IllegalArgumentException")
        void wrongCurrentPassword_throws() {
            UserAuth stored      = userAuth(customerId);
            UserAuth currentUser = userAuth(customerId);

            when(userAuthRepository.findByUsername(currentUser.getUsername()))
                    .thenReturn(Optional.of(stored));
            when(passwordEncoder.matches("wrongPassword", stored.getPasswordHash()))
                    .thenReturn(false);

            assertThatThrownBy(() -> useCase.changePassword(
                    customerId,
                    changePasswordRequest("wrongPassword", "newPass1", "newPass1"),
                    currentUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Current password is incorrect");

            verify(userAuthRepository, never()).updatePassword(any(), any());
        }

        @Test
        @DisplayName("happy path: encodes new password and calls updatePassword")
        void happyPath_updatesPassword() {
            UserAuth stored      = userAuth(customerId);
            UserAuth currentUser = userAuth(customerId);
            String   encoded     = "$2a$10$newEncoded";

            when(userAuthRepository.findByUsername(currentUser.getUsername()))
                    .thenReturn(Optional.of(stored));
            when(passwordEncoder.matches("currentPass", stored.getPasswordHash()))
                    .thenReturn(true);
            when(passwordEncoder.encode("newPass123")).thenReturn(encoded);

            assertThatCode(() -> useCase.changePassword(
                    customerId,
                    changePasswordRequest("currentPass", "newPass123", "newPass123"),
                    currentUser))
                    .doesNotThrowAnyException();

            verify(userAuthRepository).updatePassword(stored.getId(), encoded);
        }

        @Test
        @DisplayName("user not found in repository throws IllegalArgumentException")
        void userNotFound_throws() {
            UserAuth currentUser = userAuth(customerId);

            when(userAuthRepository.findByUsername(currentUser.getUsername()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.changePassword(
                    customerId,
                    changePasswordRequest("current", "newPass1", "newPass1"),
                    currentUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus")
    class UpdateStatus {

        @Test
        @DisplayName("happy path: calls updateActive with the requested value")
        void happyPath_updatesActive() {
            when(userAuthRepository.findByUsername("0901234567"))
                    .thenReturn(Optional.of(userAuth(customerId)));

            useCase.updateStatus("0901234567", statusRequest(false));

            verify(userAuthRepository).updateActive("0901234567", false);
        }

        @Test
        @DisplayName("user not found throws IllegalArgumentException")
        void userNotFound_throws() {
            when(userAuthRepository.findByUsername("0901234567")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.updateStatus("0901234567", statusRequest(true)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // ── deleteCustomer ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteCustomer")
    class DeleteCustomer {

        @Test
        @DisplayName("happy path: sets user inactive via updateActive(false)")
        void happyPath_deactivates() {
            when(userAuthRepository.findByUsername("0901234567"))
                    .thenReturn(Optional.of(userAuth(customerId)));

            useCase.deleteCustomer("0901234567");

            verify(userAuthRepository).updateActive("0901234567", false);
        }

        @Test
        @DisplayName("user not found throws IllegalArgumentException")
        void userNotFound_throws() {
            when(userAuthRepository.findByUsername("0901234567")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.deleteCustomer("0901234567"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }
    }
}
