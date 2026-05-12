package com.bvb.accountmodule.service.usecase;

import com.bvb.accountmodule.domain.Account;
import com.bvb.accountmodule.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTransactionService {

    private final AccountRepository accountRepository;

    // ── Originating operations ────────────────────────────────────────────────

    @Transactional
    public BigDecimal deposit(UUID accountId, BigDecimal amount, UUID transactionId) {
        Account account = lockAccount(accountId);
        validateActive(account);
        account.credit(amount);
        accountRepository.save(account);
        log.info("[ACCOUNT] Deposit accountId={} amount={} txId={}", accountId, amount, transactionId);
        return BigDecimal.ZERO;
    }

    @Transactional
    public BigDecimal withdraw(UUID accountId, BigDecimal amount, UUID transactionId) {
        Account account = lockAccount(accountId);
        validateActive(account);
        account.debit(amount);
        accountRepository.save(account);
        log.info("[ACCOUNT] Withdraw accountId={} amount={} txId={}", accountId, amount, transactionId);
        return BigDecimal.ZERO;
    }

    @Transactional
    public BigDecimal transfer(UUID sourceId, UUID targetId, BigDecimal amount, UUID transactionId) {
        List<UUID> orderIds = sortAccountIds(sourceId, targetId);

        //Lock by order to prevent deadlock
        Account first = lockAccount(orderIds.get(0));
        Account second = lockAccount(orderIds.get(1));

        Account source = orderIds.get(0).equals(sourceId) ? first : second;
        Account target = orderIds.get(0).equals(targetId) ? first : second;

        validateActive(source);
        validateActive(target);
        source.debit(amount);
        accountRepository.save(source);

        target.credit(amount);
        accountRepository.save(target);

        log.info("[ACCOUNT] Transfer source={} target={} amount={} txId={}", sourceId, targetId, amount, transactionId);
        return BigDecimal.ZERO;
    }

    // ── Reversal operations (for rollback) ────────────────────────────────────

    @Transactional
    public void reverseDeposit(UUID accountId, BigDecimal amount, UUID transactionId) {
        Account account = lockAccount(accountId);
        account.debit(amount);
        accountRepository.save(account);
        log.info("[ACCOUNT] ReverseDeposit accountId={} amount={} txId={}", accountId, amount, transactionId);
    }

    @Transactional
    public void reverseWithdraw(UUID accountId, BigDecimal amount, UUID transactionId) {
        Account account = lockAccount(accountId);
        account.credit(amount);
        accountRepository.save(account);
        log.info("[ACCOUNT] ReverseWithdraw accountId={} amount={} txId={}", accountId, amount, transactionId);
    }

    @Transactional
    public void reverseTransfer(UUID sourceId, UUID targetId, BigDecimal amount, UUID transactionId) {
        List<UUID> orderIds = sortAccountIds(sourceId, targetId);

        //Lock by order to prevent deadlock
        Account first = lockAccount(orderIds.get(0));
        Account second = lockAccount(orderIds.get(1));


        Account source = orderIds.get(0).equals(sourceId) ? first : second;
        Account target = orderIds.get(0).equals(targetId) ? first : second;

        validateActive(source);
        validateActive(target);

        source.credit(amount);
        accountRepository.save(source);

        target.debit(amount);
        accountRepository.save(target);
        log.info("[ACCOUNT] ReverseTransfer source={} target={} amount={} txId={}", sourceId, targetId, amount, transactionId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Account lockAccount(UUID accountId) {
        return accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    private void validateActive(Account account) {
        if (!account.isActive()) {
            throw new IllegalStateException("Account is not active: " + account.getId());
        }
    }

    public List<UUID> sortAccountIds(UUID sourceId, UUID targetId) {
        if (sourceId.compareTo(targetId) < 0) {
            return List.of(sourceId, targetId);
        }
        else {
            return List.of(targetId, sourceId);
        }
    }
}
