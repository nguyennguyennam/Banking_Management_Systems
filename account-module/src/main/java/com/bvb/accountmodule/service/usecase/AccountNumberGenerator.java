package com.bvb.accountmodule.service.usecase;

import com.bvb.accountmodule.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;

    // Seeded from max existing account number in DB on startup
    private final AtomicLong counter = new AtomicLong(0);
    private volatile boolean initialized = false;

    public synchronized String generate() {
        if (!initialized) {
            long max = accountRepository.findMaxAccountNumberSuffix().orElse(1000000000L);
            counter.set(max);
            initialized = true;
        }

        long next = counter.incrementAndGet();
        if (next > 9999999999L) {
            throw new IllegalStateException("Account number exhausted");
        }
        return String.format("%010d", next);
    }
}