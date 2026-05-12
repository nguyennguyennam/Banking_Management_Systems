package com.bvb.accountmodule.service;

import com.bvb.accountmodule.domain.Account;
import com.bvb.accountmodule.repository.AccountRepository;
import com.bvb.accountmodule.service.DTO.request.CreateAccountRequest;
import com.bvb.accountmodule.service.DTO.request.UpdateAccountRequest;
import com.bvb.accountmodule.service.DTO.response.AccountResponse;
import com.bvb.accountmodule.service.usecase.AccountNumberGenerator;
import com.bvb.accountmodule.service.usecase.AccountUseCase;
import com.bvb.customermodule.domain.Customer;
import com.bvb.customermodule.domain.CustomerType;
import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.repository.CustomerRepository;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.sharedmodule.config.redis.AuditLogPublisher;
import com.bvb.sharedmodule.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountUseCase")
class AccountUseCaseTest {

    @Mock AccountRepository      accountRepository;
    @Mock CustomerRepository     customerRepository;
    @Mock UserAuthRepository     userAuthRepository;
    @Mock AccountNumberGenerator accountNumberGenerator;
    @Mock AuditLogPublisher      auditLogPublisher;
    @Mock ObjectMapper           objectMapper;

    @InjectMocks AccountUseCase useCase;

    private UUID customerId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        accountId  = UUID.randomUUID();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CustomerType customerType(BigDecimal limit) {
        return CustomerType.builder()
                .id(UUID.randomUUID())
                .typeName(CustomerType.TypeName.INDIVIDUAL)
                .maxTransactionLimit(limit)
                .dailyLimit(new BigDecimal("50000.00"))
                .build();
    }

    private Customer customer(UUID id, CustomerType type) {
        return Customer.builder()
                .id(id)
                .customerType(type)
                .fullName("Test User")
                .email("test@test.com")
                .phone("0901234567")
                .address("123 Main St")
                .city("HCM")
                .nationalId("123456789012")
                .build();
    }

    private Account account(UUID id, BigDecimal balance, Account.Status status) {
        return Account.builder()
                .id(id)
                .customerId(customerId)
                .accountNumber("1234567890")
                .accountType(Account.AccountType.PAYMENT)
                .balance(balance)
                .transactionLimit(new BigDecimal("5000.00"))
                .status(status)
                .build();
    }

    private CreateAccountRequest createRequest() {
        CreateAccountRequest req = new CreateAccountRequest();
        ReflectionTestUtils.setField(req, "customerId",     customerId);
        ReflectionTestUtils.setField(req, "initialBalance", new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(req, "changedBy",      "admin");
        ReflectionTestUtils.setField(req, "changedByRole",  "ADMIN");
        return req;
    }

    private UserAuth userAuth(UUID ownedCustomerId) {
        return UserAuth.builder()
                .id(UUID.randomUUID())
                .customerId(ownedCustomerId)
                .username("0901234567")
                .passwordHash("hash")
                .role(Role.USER)
                .active(true)
                .build();
    }

    // ── createAccount ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAccount")
    class CreateAccount {

        @Test
        @DisplayName("happy path: saves account and publishes audit event")
        void happyPath_savesAndPublishes() {
            CustomerType type = customerType(new BigDecimal("5000.00"));
            Customer     cust = customer(customerId, type);

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(cust));
            when(accountNumberGenerator.generate()).thenReturn("1234567890");
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AccountResponse response = useCase.createAccount(createRequest());

            assertThat(response.getCustomerId()).isEqualTo(customerId);
            assertThat(response.getBalance()).isEqualByComparingTo("1000.00");
            assertThat(response.getStatus()).isEqualTo(Account.Status.ACTIVE);
            verify(accountRepository).save(any(Account.class));
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("customer not found throws IllegalArgumentException")
        void customerNotFound_throws() {
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.createAccount(createRequest()))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(accountRepository, auditLogPublisher);
        }
    }

    // ── closeAccount ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("closeAccount")
    class CloseAccount {

        @Test
        @DisplayName("zero balance: soft-deletes and publishes audit")
        void zeroBalance_softDeletes() {
            Account a = account(accountId, BigDecimal.ZERO, Account.Status.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(a));

            useCase.closeAccount(accountId, "admin", "ADMIN");

            verify(accountRepository).softDelete(accountId);
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("nonzero balance throws IllegalStateException")
        void nonzeroBalance_throws() {
            Account a = account(accountId, new BigDecimal("100.00"), Account.Status.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> useCase.closeAccount(accountId, "admin", "ADMIN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot close account with remaining balance");

            verify(accountRepository, never()).softDelete(any());
        }
    }

    // ── updateLimit ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateLimit")
    class UpdateLimit {

        private UpdateAccountRequest request(BigDecimal limit) {
            UpdateAccountRequest req = new UpdateAccountRequest();
            ReflectionTestUtils.setField(req, "transactionLimit", limit);
            ReflectionTestUtils.setField(req, "changedBy",        "admin");
            ReflectionTestUtils.setField(req, "changedByRole",    "ADMIN");
            return req;
        }

        @Test
        @DisplayName("within customer type max: calls update and publishes audit")
        void withinMax_updatesAndPublishes() {
            CustomerType type = customerType(new BigDecimal("5000.00"));
            Customer     cust = customer(customerId, type);
            Account      acc  = account(accountId, new BigDecimal("500.00"), Account.Status.ACTIVE);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(cust));

            useCase.updateLimit(accountId, request(new BigDecimal("3000.00")));

            verify(accountRepository).updateAccountTransactionLimit(accountId, new BigDecimal("3000.00"));
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("exceeds customer type max throws IllegalArgumentException")
        void exceedsMax_throws() {
            CustomerType type = customerType(new BigDecimal("5000.00"));
            Customer     cust = customer(customerId, type);
            Account      acc  = account(accountId, new BigDecimal("500.00"), Account.Status.ACTIVE);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
            when(customerRepository.findById(customerId)).thenReturn(Optional.of(cust));

            assertThatThrownBy(() -> useCase.updateLimit(accountId, request(new BigDecimal("6000.00"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Transaction limit exceeds customer type maximum");

            verify(accountRepository, never()).updateAccountTransactionLimit(any(), any());
        }
    }

    // ── lock / unlock ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("lock and unlock")
    class LockUnlock {

        @Test
        @DisplayName("lock: sets account status to LOCKED")
        void lock_setsLocked() {
            Account acc = account(accountId, new BigDecimal("500.00"), Account.Status.ACTIVE);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AccountResponse response = useCase.lock(accountId, "admin", "ADMIN");

            assertThat(response.getStatus()).isEqualTo(Account.Status.LOCKED);
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("unlock: sets account status to ACTIVE")
        void unlock_setsActive() {
            Account acc = account(accountId, new BigDecimal("500.00"), Account.Status.LOCKED);
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(acc));
            when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AccountResponse response = useCase.unlock(accountId, "admin", "ADMIN");

            assertThat(response.getStatus()).isEqualTo(Account.Status.ACTIVE);
            verify(auditLogPublisher).publish(any());
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById: not found throws IllegalArgumentException")
    void findById_notFound_throws() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.findById(accountId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }

    // ── verifyOwnership ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("verifyOwnership")
    class VerifyOwnership {

        private static final String PHONE = "0901234567";

        @Test
        @DisplayName("authenticated user not found throws 401")
        void userNotFound_throws401() {
            when(userAuthRepository.findByUsername(PHONE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.verifyOwnership(accountId, PHONE))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        @DisplayName("account not found throws 404")
        void accountNotFound_throws404() {
            when(userAuthRepository.findByUsername(PHONE)).thenReturn(Optional.of(userAuth(customerId)));
            when(accountRepository.existsById(accountId)).thenReturn(false);

            assertThatThrownBy(() -> useCase.verifyOwnership(accountId, PHONE))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("account belongs to different customer throws 403")
        void mismatchedCustomer_throws403() {
            UUID otherCustomerId = UUID.randomUUID();
            when(userAuthRepository.findByUsername(PHONE)).thenReturn(Optional.of(userAuth(otherCustomerId)));
            when(accountRepository.existsById(accountId)).thenReturn(true);
            when(accountRepository.existsByIdAndCustomerId(accountId, otherCustomerId)).thenReturn(false);

            assertThatThrownBy(() -> useCase.verifyOwnership(accountId, PHONE))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("valid ownership: completes without exception")
        void validOwnership_noException() {
            when(userAuthRepository.findByUsername(PHONE)).thenReturn(Optional.of(userAuth(customerId)));
            when(accountRepository.existsById(accountId)).thenReturn(true);
            when(accountRepository.existsByIdAndCustomerId(accountId, customerId)).thenReturn(true);

            assertThatCode(() -> useCase.verifyOwnership(accountId, PHONE))
                    .doesNotThrowAnyException();
        }
    }
}
