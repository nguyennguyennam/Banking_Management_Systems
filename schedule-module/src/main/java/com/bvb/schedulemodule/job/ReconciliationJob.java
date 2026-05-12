package com.bvb.schedulemodule.job;

import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scans PENDING transactions older than STALE_THRESHOLD_MINUTES and marks them FAILED.
 * With fire-and-forget event delivery, a stale PENDING means the event was lost before
 * the account-module could process it, so no balance mutation occurred.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private static final int STALE_THRESHOLD_MINUTES = 2;
    private static final int BATCH_SIZE              = 100;

    private final TransactionRepository transactionRepository;

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<com.bvb.transactionmodule.domain.Transaction> stale = transactionRepository
                .findStalePending(cutoff, PageRequest.of(0, BATCH_SIZE));

        if (stale.isEmpty()) return;

        log.info("[RECONCILIATION] Marking {} stale PENDING transactions as FAILED (cutoff={})",
                stale.size(), cutoff);

        for (Transaction tx : stale) {
            try {
                int updated = transactionRepository.markFailed(tx.getId());
                if (updated > 0) {
                    log.info("[RECONCILIATION] FAILED txId={} type={}", tx.getId(), tx.getTransactionType());
                }
            } catch (Exception e) {
                log.error("[RECONCILIATION] Failed to mark txId={} error={}", tx.getId(), e.getMessage());
            }
        }
    }
}
