package com.bvb.accountmodule.service.usecase;

import com.bvb.accountmodule.domain.Account;
import com.bvb.accountmodule.repository.AccountRepository;
import com.bvb.accountmodule.service.DTO.request.CreateAccountRequest;
import com.bvb.accountmodule.service.DTO.request.UpdateAccountRequest;
import com.bvb.accountmodule.service.DTO.response.AccountResponse;
import com.bvb.accountmodule.service.DTO.response.AccountStatsResponse;
import com.bvb.customermodule.domain.Customer;
import com.bvb.customermodule.repository.CustomerRepository;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import com.bvb.sharedmodule.config.redis.AuditLogPublisher;
import com.bvb.sharedmodule.service.response.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountUseCase {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final UserAuthRepository userAuthRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditLogPublisher auditLogPublisher;
    private final ObjectMapper objectMapper;

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "account-by-customer", allEntries = true)
    public AccountResponse createAccount(CreateAccountRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Custxomer not found: " + request.getCustomerId()));

        Account account = Account.builder()
                .id(UUID.randomUUID())
                .customerId(request.getCustomerId())
                .accountNumber(accountNumberGenerator.generate())
                .accountType(Account.AccountType.PAYMENT)
                .balance(request.getInitialBalance())
                .transactionLimit(customer.getCustomerType().getMaxTransactionLimit())
                .status(Account.Status.ACTIVE)
                .build();

        accountRepository.save(account);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(account.getId())
                .entityType(AuditLogEvent.EntityType.ACCOUNT)
                .action(AuditLogEvent.Action.CREATE)
                .newValue(toJson(account))
                .changedBy(request.getChangedBy())
                .changedByRole(request.getChangedByRole())
                .occurredAt(Instant.now())
                .build());

        log.info("Account created: {} for customer: {}", account.getId(), customer.getId());
        return AccountResponse.toResponse(account);
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "account", key = "#id")
    public AccountResponse getById(UUID id) {
        return AccountResponse.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> getAll(Pageable pageable) {
        return PageResponse.from(
                accountRepository.findAll(pageable).map(AccountResponse::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "account-by-customer",
               key = "#customerId.toString() + ':' + #pageable.pageNumber")
    public PageResponse<AccountResponse> getByCustomerId(UUID customerId, Pageable pageable) {
        return PageResponse.from(
                accountRepository.findByCustomerIdAndStatusNot(
                                customerId, Account.Status.CLOSED, pageable)
                        .map(AccountResponse::toResponse));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "account", key = "#id")
    public AccountResponse updateLimit(UUID id, UpdateAccountRequest request) {
        Account account = findById(id);

        Customer customer = customerRepository.findById(account.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer not found: " + account.getCustomerId()));

        if (request.getTransactionLimit()
                .compareTo(customer.getCustomerType().getMaxTransactionLimit()) > 0)
            throw new IllegalArgumentException("Transaction limit exceeds customer type maximum");

        String oldValue = toJson(account);
        accountRepository.updateAccountTransactionLimit(id, request.getTransactionLimit());
        Account updated = findById(id);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(id)
                .entityType(AuditLogEvent.EntityType.ACCOUNT)
                .action(AuditLogEvent.Action.UPDATE)
                .oldValue(oldValue)
                .newValue(toJson(updated))
                .changedBy(request.getChangedBy())
                .changedByRole(request.getChangedByRole())
                .reason("Transaction limit updated")
                .occurredAt(Instant.now())
                .build());

        return AccountResponse.toResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "account", key = "#id")
    public AccountResponse lock(UUID id, String changedBy, String changedByRole) {
        Account account = findById(id);
        String oldValue = toJson(account);

        account.lock();
        accountRepository.save(account);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(id)
                .entityType(AuditLogEvent.EntityType.ACCOUNT)
                .action(AuditLogEvent.Action.ACCOUNT_LOCKED)
                .oldValue(oldValue)
                .newValue(toJson(account))
                .changedBy(changedBy)
                .changedByRole(changedByRole)
                .reason("Account locked")
                .occurredAt(Instant.now())
                .build());

        return AccountResponse.toResponse(account);
    }

    @Transactional
    @CacheEvict(value = "account", key = "#id")
    public AccountResponse unlock(UUID id, String changedBy, String changedByRole) {
        Account account = findById(id);
        String oldValue = toJson(account);

        account.unlock();
        accountRepository.save(account);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(id)
                .entityType(AuditLogEvent.EntityType.ACCOUNT)
                .action(AuditLogEvent.Action.ACCOUNT_ACTIVE)
                .oldValue(oldValue)
                .newValue(toJson(account))
                .changedBy(changedBy)
                .changedByRole(changedByRole)
                .reason("Account unlocked")
                .occurredAt(Instant.now())
                .build());

        return AccountResponse.toResponse(account);
    }

    // ── SOFT DELETE ───────────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "account",              key = "#id"),
            @CacheEvict(value = "account-by-customer",  allEntries = true)
    })
    public void closeAccount(UUID id, String changedBy, String changedByRole) {
        Account account = findById(id);

        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0)
            throw new IllegalStateException("Cannot close account with remaining balance");

        String oldValue = toJson(account);
        accountRepository.softDelete(id);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(id)
                .entityType(AuditLogEvent.EntityType.ACCOUNT)
                .action(AuditLogEvent.Action.ACCOUNT_CLOSED)
                .oldValue(oldValue)
                .changedBy(changedBy)
                .changedByRole(changedByRole)
                .reason("Account closed")
                .occurredAt(Instant.now())
                .build());

        log.info("Account closed: {}", id);
    }

    // ── STATS ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(value = "account-stats", key = "'global'")
    public AccountStatsResponse getStats() {
        Map<String, Long> countByType = accountRepository.countByType().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        Map<String, Long> countByBalanceTier = new HashMap<>();
        for (Object[] row : accountRepository.getStats()) {
            countByBalanceTier.put(row[0].toString(), (Long) row[1]);
        }

        return AccountStatsResponse.builder()
                .countByType(countByType)
                .highBalance(countByBalanceTier.getOrDefault("HIGH", 0L))
                .mediumBalance(countByBalanceTier.getOrDefault("MEDIUM", 0L))
                .lowBalance(countByBalanceTier.getOrDefault("LOW", 0L))
                .build();
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    @Transactional(readOnly = true)
    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found: " + accountNumber));
    }

    @Transactional
    public void save(Account account) {
        accountRepository.save(account);
    }

    // ── Ownership verification ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void verifyOwnership(UUID accountId, String phone) {
        UUID customerId = userAuthRepository.findByUsername(phone)
                .map(u -> u.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        if (!accountRepository.existsById(accountId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Account not found: " + accountId);

        if (!accountRepository.existsByIdAndCustomerId(accountId, customerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied to account: " + accountId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }
}