package com.bvb.transactionmodule.service.usecase;

import com.bvb.sharedmodule.event.TransactionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferResultHandler {

    private final TransactionStateService stateService;

    @Async("auditTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionResult(TransactionResultEvent event) {
        log.info("[TX_RESULT] txId={} success={} accountId={}",
                event.transactionId(), event.success(), event.accountId());
        if (event.success()) {
            stateService.markCompleted(event.transactionId(), event.fee(), event.performedBy());
        } else {
            log.warn("[TX_RESULT] FAILED txId={} reason={}", event.transactionId(), event.failureReason());
            stateService.markFailed(event.transactionId());
        }
    }
}
